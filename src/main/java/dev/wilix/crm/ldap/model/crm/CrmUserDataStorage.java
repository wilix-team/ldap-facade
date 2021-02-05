package dev.wilix.crm.ldap.model.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;
import dev.wilix.crm.ldap.config.properties.UserDataStorageConfigurationProperties;
import dev.wilix.crm.ldap.model.Authentication;
import dev.wilix.crm.ldap.model.UserDataStorage;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Хранилище пользователей, построенное на основе Wilix CRM.
 */
public class CrmUserDataStorage implements UserDataStorage {
    // TODO Нужно добавить проверку у пользователей на флаг isActive
    private final static Logger LOG = LoggerFactory.getLogger(CrmUserDataStorage.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String searchUserUriTemplate;
    private final String authenticateUserUri;

    public CrmUserDataStorage(HttpClient httpClient, ObjectMapper objectMapper, UserDataStorageConfigurationProperties config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        // TODO делать безопасное формирование с учетом граничных условий.
        searchUserUriTemplate = getSearchUserUriTemplate(config.getBaseUrl());
        // todo проверить есть ли тут группы
        authenticateUserUri = config.getBaseUrl() + "/api/v1/App/user";
    }

    private static String getSearchUserUriTemplate(String uri) {
        try {
            URIBuilder builder = new URIBuilder(uri);
            builder.setScheme("https");
            builder.setPath("/api/v1/User");
            builder.addParameter("select", "mailAddress,teamsIds");
            builder.addParameter("where[0][attribute]", "%s");
            builder.addParameter("where[0][type]", "equals");
            String searchUserUri = builder.build().toURL().toString();
            return java.net.URLDecoder.decode(searchUserUri, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.debug("Problem with URIBuilder: {}", uri);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
    }

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            Map<String, List<String>> userInfo = authenticateWithUserCredentials(userName, password);

            users.put(userName, userInfo);

            UserAuthentication auth = new UserAuthentication();
            auth.setSuccess(true);
            auth.setUserName(userName);
            auth.setPassword(password);

            return auth;
        } catch (Exception ex) {
            LOG.debug("Wrong user credentials: {}:{}", userName, password);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        try {
            Map<String, List<String>> userInfo = authenticateWithServiceToken(serviceName, token);

            users.put(serviceName, userInfo);

            ServiceAuthentication auth = new ServiceAuthentication();
            auth.setSuccess(true);
            auth.setServiceName(serviceName);
            auth.setToken(token);

            return auth;
        } catch (Exception ex) {
            LOG.debug("Wrong service credentials: {}:{}", serviceName, token);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Map<String, List<String>> getInfo(String userName, Authentication authentication) {

        if (!canSearch(authentication, userName)) {
            throw new IllegalStateException("User tries to get another user.");
        }

        Map<String, List<String>> info = users.getIfPresent(userName);
        if (info == null) {
            info = searchUser(userName, authentication);
            if (info != null && !info.isEmpty()) {
                users.put(userName, info);
            }
        }
        return info;
    }

    private boolean canSearch(Authentication authentication, String username) {
        if (authentication instanceof ServiceAuthentication) {
            return true;
        }
        if (authentication instanceof UserAuthentication) {
            return ((UserAuthentication) authentication).getUserName().equals(username);
        }
        throw new IllegalStateException("Unknown authentication format.");
    }

    private Map<String, List<String>> authenticateWithUserCredentials(String userName, String password) {
        HttpRequest.Builder requestBuilder = prepareAuthenticateRequest();

        final String base64Credentials = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        requestBuilder.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64Credentials);

        return requestUserInfo(requestBuilder.build(), responseNode -> responseNode.get("user"));
    }

    private Map<String, List<String>> authenticateWithServiceToken(String serviceName, String apiKey) {
        HttpRequest.Builder requestBuilder = prepareAuthenticateRequest();

        requestBuilder.setHeader("X-Api-Key", apiKey);

        return requestUserInfo(requestBuilder.build(), responseNode -> responseNode.get("user"));
    }

    private HttpRequest.Builder prepareAuthenticateRequest() {
        return prepareBasicCrmRequest(authenticateUserUri);
    }

    private Map<String, List<String>> searchUser(String userName, Authentication authentication) {
        boolean canSearch;
        try {
            canSearch = canSearch(authentication, userName);
        } catch (Exception e) {
            LOG.error("Unknown authentication format.");
            throw new IllegalStateException("Unknown authentication format.");
        }
        if (!canSearch) {
            throw new IllegalStateException("Can't search user info with non ServiceAccount.");
        }

        HttpRequest.Builder request = prepareUserSearchRequest(userName);

        String token = ((ServiceAuthentication) authentication).getToken();
        request.setHeader("X-Api-Key", token);

        return requestUserInfo(request.build(), responseNode -> responseNode.get("list").get(0));
    }

    private HttpRequest.Builder prepareUserSearchRequest(String userName) {
        return prepareBasicCrmRequest(
                String.format(searchUserUriTemplate, userName));
    }

    private HttpRequest.Builder prepareBasicCrmRequest(String requestUri) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(requestUri))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8");
    }

    private Map<String, List<String>> requestUserInfo(HttpRequest request, Function<JsonNode, JsonNode> userNodeExtractor) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Get errors when trying to communicate with CRM!", ex);
        }

        LOG.debug("Receive response from CRM: {}", response.body());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            LOG.warn("For request {} received UNAUTHORIZED response", request);
            throw new IllegalStateException("Incorrect credentials or access rights!");
        }

        if (response.statusCode() != 200) {
            LOG.warn("For request {} receive bad response from CRM {}", request, response);
            throw new IllegalStateException("Get bad request from CRM!");
        }

        try {
            String responseBody = response.body();

            JsonNode responseJsonNode = objectMapper.readTree(responseBody);
            JsonNode userJson = userNodeExtractor.apply(responseJsonNode);

            return parseUserInfoInCrmFormatFromJsonNode(userJson);
        } catch (Exception e) {
            LOG.warn("Cant save {} info {}", request, response.body());
            throw new IllegalStateException("Can't properly parse CRM response.");
        }
    }

    /**
     * Парсинг пользователя из формата ответа от CRM.
     *
     * @param userJsonField Json поле с информацией о пользователе.
     * @return Разобранная информация о пользователе в ожидаемом формате.
     */
    private Map<String, List<String>> parseUserInfoInCrmFormatFromJsonNode(JsonNode userJsonField) {
        Map<String, List<String>> info = new HashMap<>();

        if (userJsonField != null) {
            // Приемник для корректной установки свойств пользователя из ответа сервера в свойства пользователя для ldap.
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = userJsonField.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            info.put("company", List.of("WILIX"));

            jsonToUserFieldSetter.accept("userName", value -> info.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));
            jsonToUserFieldSetter.accept("emailAddress", value -> info.put("mail", List.of(value)));

            List<String> memberOfList = new ArrayList<>();
            JsonNode teamsNamesNode = userJsonField.get("teamsNames");
            if (teamsNamesNode != null) {
                for (JsonNode teamNameNode : teamsNamesNode) {
                    memberOfList.add(teamNameNode.textValue());
                }
            }

            info.put("memberOf", memberOfList);

            // атрибут для имени в vcs системах (git)
            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            info.put("vcsName", vcsName);
        }

        return info;
    }
}