package dev.wilix.crm.ldap.model.crm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;
import dev.wilix.crm.ldap.config.properties.UserDataStorageConfigurationProperties;
import dev.wilix.crm.ldap.model.Authentication;
import dev.wilix.crm.ldap.model.UserDataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Хранилище пользователей, построенное на основе Wilix CRM.
 */
public class CrmUserDataStorage implements UserDataStorage {
    // TODO Нужно добавить проверку у пользователей на флаг isActive
    private final static Logger LOG = LoggerFactory.getLogger(CrmUserDataStorage.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String userDirectAuthUri;
    private final String baseUrl;

    private final String searchUserUriTemplate;
    private final String authenticateUserUri;

    public CrmUserDataStorage(HttpClient httpClient, ObjectMapper objectMapper, UserDataStorageConfigurationProperties config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();
        userDirectAuthUri = config.getUserDirectAuthUri();
        baseUrl = config.getBaseUrl();

        searchUserUriTemplate = baseUrl + "/api/v1/User?select=emailAddress&where[0][attribute]=userName&where[0][value]=%s&where[0][type]=equals";
        authenticateUserUri = baseUrl + "/api/v1/App/user";
    }

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            // На текущий момент просто запрашиваем информацию о пользователе.
            UserAuthentication auth = new UserAuthentication();

            Map<String, List<String>> userInfo = authenticateWithCredentials(userName, password, auth);
            users.put(userName, userInfo);

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
            // На текущий момент просто запрашиваем информацию о пользователе.
            // FIXME Это костыль и нужен другой способ проверки проверки аутентификации сервиса.
            ServiceAuthentication auth = new ServiceAuthentication();

            Map<String, List<String>> userInfo = authenticateWithCredentials(serviceName, token, auth);
            users.put(serviceName, userInfo);

            auth.setSuccess(true);
            auth.setServiceName(serviceName);
            auth.setToken(token);

            return auth;

        } catch (Exception ex) {
            LOG.debug("Wrong service credentials: {}:{}", serviceName, token);
            return Authentication.NEGATIVE;
        }
    }

    private Map<String, List<String>> authenticateWithCredentials(String userName, String confirmation, Authentication auth) {
        HttpRequest.Builder requestBuilder = prepareAuthenticateRequest();

        if (auth instanceof ServiceAuthentication) {
            requestBuilder.setHeader("X-Api-Key", confirmation);
        } else {
            String base64Credentials = Base64.getEncoder().encodeToString((userName + ":" + confirmation).getBytes(StandardCharsets.UTF_8));
            requestBuilder.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64Credentials);
        }

        return requestUserInfo(requestBuilder.build());
    }

    private HttpRequest.Builder prepareAuthenticateRequest() {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(authenticateUserUri))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8");
    }

    private Map<String, List<String>> requestUserInfo(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Get errors when trying to communicate with CRM!", ex);
        }

        LOG.debug("Receive response from CRM: {}", response);

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
            JsonNode userJson = getUserJsonNode(responseJsonNode);

            return parseUserInfoInCrmFormatFromJsonNode(userJson);
        } catch (Exception e) {
            LOG.warn("Cant save {} info {}", request, response.body());
            throw new IllegalStateException("Can't properly parse CRM response.");
        }
    }

    private JsonNode getUserJsonNode(JsonNode responseNode) {
        // При авторизации по пути App/user информация о пользователе будет лежать по этому пути.
        JsonNode userJson = responseNode.get("user");

        // Если происходит поиск среди всех пользователей (сервисный аккаунт), то результаты поиска будут лежать по другому пути.
        if (userJson == null) {
            userJson = responseNode.get("list").get(0);
        }

        if (userJson == null) {
            throw new IllegalArgumentException("Json node can't be null.");
        }

        return userJson;
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

            // атрибут для имени в vcs системах (git)
            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            info.put("vcsName", vcsName);

            // TODO Группы!!!
        }

        return info;
    }

    private Map<String, List<String>> searchUser(String username, Authentication authentication) {
        if (authentication instanceof ServiceAuthentication) {
            String token = ((ServiceAuthentication) authentication).getToken();

            try {
                return getInfoByRequestToCRM(username, token);
            } catch (IOException | InterruptedException e) {
                LOG.warn("Can't get user info for {} by service", username);
            }

        }

        return null;
    }

    private Map<String, List<String>> getInfoByRequestToCRM(String username, String bindPassword) throws IOException, InterruptedException {
        HttpRequest request = buildHttpRequestToCRM(username, bindPassword);

        LOG.info("Send authentication request to CRM for {}", username);

        return requestUserInfoFromCRM(request, username);
    }

    private HttpRequest buildHttpRequestToCRM(String username, String bindPassword) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(buildRequestURItoCRM(username))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("X-Api-Key", bindPassword)
                .build();
    }

    private URI buildRequestURItoCRM(String userName) {
        return URI.create(String.format(searchUserUriTemplate, userName));
    }

    private Map<String, List<String>> requestUserInfoFromCRM(HttpRequest crmRequest, String username) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(crmRequest, HttpResponse.BodyHandlers.ofString());

        LOG.info("Receive response from CRM: {}", response);

        if (response.statusCode() != 200) {
            LOG.warn("For username {} receive bad response from CRM {}", username, response);
            return null;
        }

        try {
            return parseUserInfo(response.body());
        } catch (Exception e) {
            LOG.warn("Cant save {} info {}", username, response.body());
            return null;
        }
    }

    private Map<String, List<String>> parseUserInfo(String responseBody) throws JsonProcessingException {
        JsonNode responseJsonNode = objectMapper.readTree(responseBody);
        JsonNode userJson = getUserJsonNode(responseJsonNode);

        Map<String, List<String>> info = parseUserInfoInCrmFormatFromJsonNode(userJson);
        LOG.info("Successfully parse info: {}", info);

        return info;
    }

    @Override
    public Map<String, List<String>> getInfo(String username, Authentication authentication) {
        var info = users.getIfPresent(username);

        if (info == null) {
            if (authentication instanceof ServiceAuthentication) {
                info = searchUser(username, authentication);
            }

            if (info != null && !info.isEmpty()) {
                users.put(username, info);
            }
        }

        return info;
    }

}