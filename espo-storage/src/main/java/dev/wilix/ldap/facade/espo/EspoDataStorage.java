package dev.wilix.ldap.facade.espo;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.espo.config.properties.EspoDataStorageConfigurationProperties;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Хранилище пользователей, построенное на основе Wilix CRM.
 */
public class EspoDataStorage implements DataStorage {
    // TODO Нужно добавить проверку у пользователей на флаг isActive
    private final static Logger LOG = LoggerFactory.getLogger(EspoDataStorage.class);

    private final RequestHelper requestHelper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String searchUserByLoginUriTemplate;
    private final String searchUsersByPatternUriTemplate;
    private final String authenticateUserUri;

    public EspoDataStorage(RequestHelper requestHelper, EspoDataStorageConfigurationProperties config) {
        this.requestHelper = requestHelper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        searchUserByLoginUriTemplate = getSearchUserUriTemplate(config.getBaseUrl());
        // FIXME Сделать менее грязно.
        searchUsersByPatternUriTemplate = searchUserByLoginUriTemplate.replace("equals", "like");

        try {
            authenticateUserUri = new URIBuilder(config.getBaseUrl()).setPath("/api/v1/App/user").build().toString();
        } catch (URISyntaxException e) {
            LOG.debug("Problem with URIBuilder:", e);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
    }

    private static String getSearchUserUriTemplate(String baseUri) {
        try {
            URIBuilder builder = new URIBuilder(baseUri);
            builder.setScheme("https");
            builder.setPath("/api/v1/User");
            builder.addParameter("select", "emailAddress,teamsIds");
            builder.addParameter("where[0][attribute]", "userName");
            builder.addParameter("where[0][value]", "%s");
            builder.addParameter("where[0][type]", "equals");
            String searchUserUri = builder.build().toURL().toString();
            return java.net.URLDecoder.decode(searchUserUri, StandardCharsets.UTF_8);
        } catch (URISyntaxException | MalformedURLException e) {
            LOG.debug("Problem with URIBuilder:", e);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
    }

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            UserAuthentication auth = new UserAuthentication();
            auth.setUserName(userName);
            auth.setPassword(password);

            // Если вернется без ошибки - все хорошо.
            Map<String, List<String>> userInfo = checkAuthentication(auth);

            if (userInfo == null || userInfo.isEmpty()) {
                throw new IllegalStateException("Unexpected result from user authentication");
            }

            users.put(userName, userInfo);

            auth.setSuccess(true);
            return auth;
        } catch (Exception ex) {
            LOG.debug("Wrong user credentials: {}:{}", userName, password);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        try {
            ServiceAuthentication auth = new ServiceAuthentication();
            auth.setServiceName(serviceName);
            auth.setToken(token);

            // Если вернется без ошибки - все хорошо.
            Map<String, List<String>> userInfo = checkAuthentication(auth);

            if (userInfo == null || userInfo.isEmpty()) {
                throw new IllegalStateException("Unexpected result from service authentication");
            }

            users.put(serviceName, userInfo);

            auth.setSuccess(true);
            return auth;
        } catch (Exception ex) {
            LOG.debug("Wrong service credentials: {}:{}", serviceName, token);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Map<String, List<String>> getSingleUserInfo(String userName, Authentication authentication) {
        Map<String, List<String>> info = users.getIfPresent(userName);
        if (info == null) {
            List<Map<String, List<String>>> users = performUsersSearch(String.format(searchUserByLoginUriTemplate, userName), authentication);
            if (users.isEmpty()) {
                throw new IllegalStateException("Can't find any user with name " + userName);
            }

            info = users.get(0);
            if (!info.isEmpty()) {
                this.users.put(userName, info);
            }
        }
        return info;
    }

    @Override
    public List<Map<String, List<String>>> getUserInfoByTemplate(String template, Authentication authentication) {
        // '*' -> '%'(SQL syntax) -> '%25' (URL encoding)
        String encodedTemplate = template.replace("*", "%25");

        List<Map<String, List<String>>> foundedUsers = performUsersSearch(String.format(searchUsersByPatternUriTemplate, encodedTemplate), authentication);

        // Кладем в кэш.
        foundedUsers.forEach(user -> users.put(user.get("uid").get(0), user));

        return foundedUsers;
    }

    @Override
    public Map<String, List<String>> getSingleGroup(String groupName, Authentication authentication) {
        return null;
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        return null;
    }

    private Map<String, List<String>> checkAuthentication(Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequest(authenticateUserUri, authentication);
        return EntityParser.parseUserInfo(response.get("user"));
    }

    private List<Map<String, List<String>>> performUsersSearch(String url, Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequest(url, authentication);

        return StreamSupport.stream(response.get("list").spliterator(), false)
                .map(EntityParser::parseUserInfo)
                .collect(Collectors.toList());
    }

}