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
 *
 * TODO Требуется перейти на ignoreCaseMap
 */
public class EspoDataStorage implements DataStorage {
    // TODO Нужно добавить проверку у пользователей на флаг isActive
    private final static Logger LOG = LoggerFactory.getLogger(EspoDataStorage.class);

    private final RequestHelper requestHelper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String authenticateUserUri;

    private final String searchUserByLoginUriTemplate;
    private final String searchUsersByPatternUriTemplate;

    private final String searchGroupByNameUriTemplate;
    private final String searchAllGroupsUri;

    public EspoDataStorage(RequestHelper requestHelper, EspoDataStorageConfigurationProperties config) {
        this.requestHelper = requestHelper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        try {
            authenticateUserUri = new URIBuilder(config.getBaseUrl()).setPath("/api/v1/App/user").build().toString();

            searchUserByLoginUriTemplate = getSearchUserUriTemplate(config.getBaseUrl());
            // FIXME Сделать менее грязно.
            searchUsersByPatternUriTemplate = searchUserByLoginUriTemplate.replace("equals", "like");

            searchGroupByNameUriTemplate = getSearchGroupUriTemplate(config.getBaseUrl());
            searchAllGroupsUri = new URIBuilder(config.getBaseUrl()).setPath("/api/v1/Team").build().toString();
        } catch (URISyntaxException e) {
            LOG.debug("Problem with URIBuilder:", e);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
    }

    // FIXME Это копипаста - переделать!!!
    private static String getSearchGroupUriTemplate(String baseUri) {
        try {
            URIBuilder builder = new URIBuilder(baseUri);
            builder.setScheme("https");
            builder.setPath("/api/v1/Team");
            builder.addParameter("where[0][attribute]", "name");
            builder.addParameter("where[0][value]", "%s");
            builder.addParameter("where[0][type]", "equals");
            String searchUserUri = builder.build().toURL().toString();
            return java.net.URLDecoder.decode(searchUserUri, StandardCharsets.UTF_8);
        } catch (URISyntaxException | MalformedURLException e) {
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

    private List<Map<String, List<String>>> foundedUsersCache;

    @Override
    public List<Map<String, List<String>>> getUserInfoByTemplate(String template, Authentication authentication) {
        if (foundedUsersCache != null) {
            return foundedUsersCache;
        }

        // '*' -> '%'(SQL syntax) -> '%25' (URL encoding)
        String encodedTemplate = template.replace("*", "%25");

        List<Map<String, List<String>>> foundedUsers = performUsersSearch(String.format(searchUsersByPatternUriTemplate, encodedTemplate), authentication);

        // Кладем в кэш.
//        foundedUsers.forEach(user -> users.put(user.get("uid").get(0), user));

        foundedUsersCache = foundedUsers;

        return foundedUsers;
    }

    @Override
    public Map<String, List<String>> getSingleGroup(String groupName, Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequest(String.format(searchGroupByNameUriTemplate, groupName), authentication);

        // FIXME Проверить, что так можно и нормально
        return StreamSupport.stream(response.get("list").spliterator(), false)
                .map(EntityParser::parseUserInfo)
                .findFirst().orElse(null);
    }

    List<Map<String, List<String>>> groupsCache;

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        if (groupsCache != null){
            return groupsCache;
        }

        JsonNode response = requestHelper.sendCrmRequest(searchAllGroupsUri, authentication);

        // FIXME Так же требуется обогащать данными об учстниках групп.
        // TODO Сделать с помощью StreamApi от списка пользователей (там эта информация есть)

        var users = getUserInfoByTemplate("*", authentication);

        Map<String, List<String>> groupToUsers = new HashMap<>();
        for (Map<String, List<String>> user : users) {
            String userName = user.get("uid").get(0);
            LOG.warn("!!!!!! {} -> {}", userName, user.get("memberof"));
            user.get("memberof").stream()
                    .forEach(groupName -> groupToUsers
                            .computeIfAbsent(groupName, s -> new ArrayList<>()).add(userName));
        }


        List<Map<String, List<String>>> groups = StreamSupport.stream(response.get("list").spliterator(), false)
                .map(EntityParser::parseGroupInfo)
                .collect(Collectors.toList());

        groups.forEach(group -> group.put("member", groupToUsers.getOrDefault(group.get("uid").get(0), Collections.emptyList())));

        groupsCache = groups;

        return groups;
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