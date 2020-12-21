package dev.wilix.crm.ldap.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.wilix.crm.ldap.config.AppConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
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

    private final static Logger LOG = LoggerFactory.getLogger(CrmUserDataStorage.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String STAFF_URI;
    private final String CRM_URI;
    private final String CRM_TOKEN;

    public CrmUserDataStorage(HttpClient httpClient, ObjectMapper objectMapper, AppConfigurationProperties config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getExpireTime(), TimeUnit.MINUTES)
                .build();
        STAFF_URI = config.getStaffURI();
        CRM_URI = config.getCrmURI();
        CRM_TOKEN = config.getCrmToken();
    }
    
    //TODO Возможно просматривать какой-нибудь флаг заблокированности пользователя.
    @Override
    public boolean authenticate(String username, String password) throws IOException, InterruptedException {
        if (username.equals("ldap-service")) {
            if (password.equals(CRM_TOKEN)) {
                LOG.info("{} service bind", username);
                saveUser(username, serviceBody());
                return true;
            }
            LOG.warn("Wrong service password for service {}", username);
            return false;
        } else {
            return getUserInfoByAuthRequest(username, password);
        }
    }

    private void saveUser(String username, String body) throws JsonProcessingException {
        Map<String, List<String>> userInfo = parseUserInfo(body);
        LOG.info("Successfully parse user: {}", userInfo);

        users.put(username, userInfo);
    }

    private String serviceBody() {
        return "{\"result\":true,\"user\":{\"user_name\":\"LDAP\",\"first_name\":\"LDAP\",\"last_name\":\"Service\",\"fullname\":\"LDAP Service\",\"email\":\"null\"}}";
    }

    /**
     * Отправляет запрос на получение информации о пользователе с логином&паролем на STAFF_URI
     */
    private boolean getUserInfoByAuthRequest(String username, String password) throws IOException, InterruptedException {
        HttpRequest crmRequest = buildAuthHttpRequest(username, password);

        LOG.info("Send authenticate request to CRM for user: {}", username);

        return performRequest(username, crmRequest);
    }

    /**
     * Подготавливает запрос для STAFF_URI
     */
    public HttpRequest buildAuthHttpRequest(String username, String password) {
        return HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(generateAuthRequestBody(username, password)))
                .uri(URI.create(STAFF_URI))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .build();
    }

    private static String generateAuthRequestBody(String username, String password) {
        return String.format("{\"username\": \"%1$s\",\"password\": \"%2$s\"}", username, password);
    }

    /**
     * Отправляет запрос
     */
    private boolean performRequest(String username, HttpRequest crmRequest) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(crmRequest, HttpResponse.BodyHandlers.ofString());

        LOG.info("Receive response from CRM: {}", response);

        if (response.statusCode() != 200) {
            LOG.warn("For username {} receive bad response from CRM {}", username, response);
            return false;
        }

        saveUser(username, response.body());
        return true;
    }

    /**
     * Отправляет запрос от имени сервиса на CRM_URI
     */
    private void getUserInfoByApplicationRequest(String username) throws IOException, InterruptedException {
        HttpRequest crmRequest = buildApplicationHttpRequest(username);

        LOG.info("Send application request to CRM for user: {}", username);

        performRequest(username, crmRequest);
    }

    /**
     * Подготавливает запрос для CRM_URI
     */
    public HttpRequest buildApplicationHttpRequest(String username) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(generateApplicationRequestURI(username))
                .setHeader("User", "ldap-service")
                .setHeader("X-Api-Key", CRM_TOKEN)
                .build();
    }

    private URI generateApplicationRequestURI(String username) {
        return URI.create(
                CRM_URI + String.format("?select=emailAddress" +
                        "&where[0][attribute]=userName" +
                        "&where[0][value]=%s" +
                        "&where[0][type]=equals", username)
        );
    }

    /**
     * Парсинг атрибутов пользователя из CRM формата в LDAP формат.
     */
    private Map<String, List<String>> parseUserInfo(String body) throws JsonProcessingException {
        Map<String, List<String>> userInfo = new HashMap<>();
        userInfo.put("company", List.of("WILIX"));

        JsonNode responseNode = objectMapper.readTree(body);
        boolean isAPI = false;

        //Auth request
        JsonNode userJsonField = responseNode.get("user");

        //Application request
        if (userJsonField == null) {
            isAPI = true;
            userJsonField = responseNode.get("list").get(0);
        }

        if (userJsonField != null) {
            JsonNode finalUserJsonField = userJsonField;
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = finalUserJsonField.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            if (isAPI) {
                parseFromAPI(userInfo, jsonToUserFieldSetter);
            } else {
                parseFromCRM(userInfo, jsonToUserFieldSetter);
            }
        }

        return userInfo;
    }

    private void parseFromAPI(Map<String, List<String>> userInfo, BiConsumer<String, Consumer<String>> jsonToUserFieldSetter) {
        jsonToUserFieldSetter.accept("userName", value -> userInfo.put("uid", List.of(value)));
        jsonToUserFieldSetter.accept("name", value -> userInfo.put("cn", List.of(value)));
        jsonToUserFieldSetter.accept("emailAddress", value -> userInfo.put("mail", List.of(value)));

        // атрибут для имени в vcs системах (git)
        // youtrack несколько имен понимает через символ перевода каретки. Подстраиваемся под этот формат.
        StringBuilder vcsName = new StringBuilder();
        jsonToUserFieldSetter.accept("name", value -> vcsName.append(value));
        jsonToUserFieldSetter.accept("emailAddress", value -> vcsName.append("\n").append(value));
        userInfo.put("vcsName", Collections.singletonList(vcsName.toString()));

        // TODO Группы!!!
    }

    private void parseFromCRM(Map<String, List<String>> userInfo, BiConsumer<String, Consumer<String>> jsonToUserFieldSetter) {
        jsonToUserFieldSetter.accept("user_name", value -> userInfo.put("uid", List.of(value)));
        jsonToUserFieldSetter.accept("fullname", value -> userInfo.put("cn", List.of(value)));
        jsonToUserFieldSetter.accept("email", value -> userInfo.put("mail", List.of(value)));

        // атрибут для имени в vcs системах (git)
        // youtrack несколько имен понимает через символ перевода каретки. Подстраиваемся под этот формат.
        StringBuilder vcsName = new StringBuilder();
        jsonToUserFieldSetter.accept("user_name", value -> vcsName.append(value));
        jsonToUserFieldSetter.accept("email", value -> vcsName.append("\n").append(value));
        userInfo.put("vcsName", Collections.singletonList(vcsName.toString()));

        // TODO Группы!!!
    }

    /**
     * Пользователь уже был получен и распаршен во время аутентификации.
     * Просто получаем его.
     */
    @Override
    public Map<String, List<String>> getUserInfo(String username, boolean isService) throws IOException, InterruptedException {
        var info = users.getIfPresent(username);

        if (info == null && isService) {
            getUserInfoByApplicationRequest(username);
            info = users.getIfPresent(username);
        }

        return info;
    }

}