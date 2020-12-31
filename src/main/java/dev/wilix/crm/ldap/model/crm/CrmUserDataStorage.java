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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Хранилище пользователей, построенное на основе Wilix CRM.
 */
public class CrmUserDataStorage implements UserDataStorage {

    private final static Logger LOG = LoggerFactory.getLogger(CrmUserDataStorage.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String userDirectAuthUri;
    private final String appUserSearchUri;

    // TODO Переделать на App/user !!!
    private final String searchUserUriTemplate;

    public CrmUserDataStorage(HttpClient httpClient, ObjectMapper objectMapper, UserDataStorageConfigurationProperties config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();
        userDirectAuthUri = config.getUserDirectAuthUri();
        appUserSearchUri = config.getAppUserSearchUri();

        searchUserUriTemplate = appUserSearchUri + "/api/v1/User?select=emailAddress&where[0][attribute]=userName&where[0][value]=%s&where[0][type]=equals";
    }

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            // На текущий момент просто запрашиваем информацию о пользователе.
            // TODO Перейти на App/user
            Map<String, List<String>> userInfo = searchUserWithUserOwnCredentials(userName, password);
            users.put(userName, userInfo);

            UserAuthentication auth = new UserAuthentication();
            auth.setSuccess(true);
            auth.setUserName(userName);
            auth.setPassword(password);

            return auth;
        } catch (Exception ex) {
            // TODO log
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        try {
            // На текущий момент просто запрашиваем информацию о пользователе.
            // FIXME Это костыль и нужен другой способ проверки проверки аутентификации сервиса.
            // TODO Перейти на App/user
            Map<String, List<String>> userInfo = searchUserWithApiTokenCredentials(serviceName, token);

            ServiceAuthentication auth = new ServiceAuthentication();
            auth.setSuccess(true);
            auth.setServiceName(serviceName);
            auth.setToken(token);

            return auth;

        } catch (Exception ex) {
            // TODO log
            return Authentication.NEGATIVE;
        }
    }

    private Map<String, List<String>> searchUserWithUserOwnCredentials(String userName, String password) {
        HttpRequest.Builder requestBuilder = prepareBaseUserInfoRequest(userName);

        String encoding = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        requestBuilder.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);

        return requestUserInfo(requestBuilder.build());
    }

    private Map<String, List<String>> searchUserWithApiTokenCredentials(String userName, String token) {
        HttpRequest.Builder requestBuilder = prepareBaseUserInfoRequest(userName);

        requestBuilder.setHeader("X-Api-Key", token);

        return requestUserInfo(requestBuilder.build());
    }

    private HttpRequest.Builder prepareBaseUserInfoRequest(String userName) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(buildRequestURItoCRM(userName))
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
            // TODO log incorrect credentials
            throw new IllegalStateException("Incorrect credentials or access rights!");
        }

        if (response.statusCode() != 200) {
            LOG.warn("For request {} receive bad response from CRM {}", request, response);
            throw new IllegalStateException("Get bad request from CRM!");
        }

        try {
            return parseUserInfoFromRegularCrmRequest(response.body());
        } catch (Exception e) {
            LOG.warn("Cant save {} info {}", request, response.body());
            throw new IllegalStateException("Can't properly parse CRM response.");
        }
    }

    private Map<String, List<String>> parseUserInfoFromRegularCrmRequest(String body) throws JsonProcessingException {
        JsonNode responseNode = objectMapper.readTree(body);

        // TODO Переделать эту систему.

        //STAFF request
        JsonNode userJsonField = responseNode.get("user");

        //CRM request
        if (userJsonField == null) {
            userJsonField = responseNode.get("list").get(0);
        }

        return parseUserInfoInCrmFormatFromJsonNode(userJsonField);
    }

    /**
     * Парсинг пользователя из формата ответа от CRM.
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

    private boolean getServiceInfo(String username, String password) throws IOException, InterruptedException {
        if (getInfoByRequestToCRM(username, password)) {
            LOG.info("{} service binded", username);
            return true;
        } else {
            LOG.warn("Wrong service password for service {}", username);
            return false;
        }
    }

    private boolean getInfoByRequestToCRM(String username, String bindPassword) throws IOException, InterruptedException {
        HttpRequest request = buildHttpRequestToCRM(username, bindPassword);

        LOG.info("Send authentication request to CRM for {}", username);

        return sendRequestAndSaveInfo(request, username);
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

    private boolean sendRequestAndSaveInfo(HttpRequest crmRequest, String username) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(crmRequest, HttpResponse.BodyHandlers.ofString());

        LOG.info("Receive response from CRM: {}", response);

        if (response.statusCode() != 200) {
            LOG.warn("For username {} receive bad response from CRM {}", username, response);
            return false;
        }

        try {
            saveUser(username, response.body());
        } catch (Exception e) {
            LOG.warn("Cant save {} info {}", username, response.body());
            return false;
        }

        return true;
    }

    private void saveUser(String username, String responseBody) throws JsonProcessingException {
        Map<String, List<String>> info = parseInfo(responseBody);
        LOG.info("Successfully parse info: {}", info);

        users.put(username, info);
    }

    //TODO Возможно просматривать какой-нибудь флаг заблокированности пользователя.
    private boolean getInfoByRequestToStaff(String username, String password) throws IOException, InterruptedException {
        HttpRequest request = buildHttpRequestToStaff(username, password);

        LOG.info("Send authentication request to STAFF for {}", username);

        return sendRequestAndSaveInfo(request, username);
    }

    private HttpRequest buildHttpRequestToStaff(String username, String password) {
        return HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBodyToStaff(username, password)))
                .uri(URI.create(userDirectAuthUri))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .build();
    }

    private  String buildRequestBodyToStaff(String username, String password) {
        return String.format("{\"username\": \"%1$s\",\"password\": \"%2$s\"}", username, password);
    }

    /**
     * Парсинг атрибутов пользователя из CRM формата в LDAP формат.
     */
    private Map<String, List<String>> parseInfo(String body) throws JsonProcessingException {
        JsonNode responseNode = objectMapper.readTree(body);
        boolean isCRM = false;

        //STAFF request
        JsonNode userJsonField = responseNode.get("user");

        //CRM request
        if (userJsonField == null) {
            isCRM = true;
            userJsonField = responseNode.get("list").get(0);
        }

        Map<String, List<String>> info = new HashMap<>();

        if (userJsonField != null) {
            JsonNode finalUserJsonField = userJsonField;
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = finalUserJsonField.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            if (isCRM) {
                parseInfoFromCRM(info, jsonToUserFieldSetter);
            } else {
                parseInfoFromStaff(info, jsonToUserFieldSetter);
            }

            info.put("company", List.of("WILIX"));
        }

        return info;
    }

    private void parseInfoFromCRM(Map<String, List<String>> info, BiConsumer<String, Consumer<String>> jsonToUserFieldSetter) {
        jsonToUserFieldSetter.accept("userName", value -> info.put("uid", List.of(value)));
        jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));
        jsonToUserFieldSetter.accept("emailAddress", value -> info.put("mail", List.of(value)));

        // атрибут для имени в vcs системах (git)
        // youtrack несколько имен понимает через символ перевода каретки. Подстраиваемся под этот формат.
        StringBuilder vcsName = new StringBuilder();
        jsonToUserFieldSetter.accept("name", vcsName::append);
        jsonToUserFieldSetter.accept("emailAddress", value -> vcsName.append("\n").append(value));
        info.put("vcsName", List.of(vcsName.toString()));

        // TODO Группы!!!
    }

    private void parseInfoFromStaff(Map<String, List<String>> info, BiConsumer<String, Consumer<String>> jsonToUserFieldSetter) {
        jsonToUserFieldSetter.accept("user_name", value -> info.put("uid", List.of(value)));
        jsonToUserFieldSetter.accept("fullname", value -> info.put("cn", List.of(value)));
        jsonToUserFieldSetter.accept("email", value -> info.put("mail", List.of(value)));

        // атрибут для имени в vcs системах (git)
        // youtrack несколько имен понимает через символ перевода каретки. Подстраиваемся под этот формат.
        StringBuilder vcsName = new StringBuilder();
        jsonToUserFieldSetter.accept("user_name", vcsName::append);
        jsonToUserFieldSetter.accept("email", value -> vcsName.append("\n").append(value));
        info.put("vcsName", List.of(vcsName.toString()));

        // TODO Группы!!!
    }

    @Override
    public Map<String, List<String>> getInfo(String username, Authentication authentication) {
        var info = users.getIfPresent(username);

//        if (info == null && SERVICE_NAMES.contains(bindUser)) {
//            getInfoByRequestToCRM(username, bindPassword);
//            info = users.getIfPresent(username);
//        }

        return info;
    }

}