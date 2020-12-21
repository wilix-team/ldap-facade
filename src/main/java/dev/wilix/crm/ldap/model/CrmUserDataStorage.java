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

    private static final List<String> SERVICE_NAMES = List.of("ldap-service");

    public CrmUserDataStorage(HttpClient httpClient, ObjectMapper objectMapper, AppConfigurationProperties config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        users = CacheBuilder.newBuilder()
                .expireAfterAccess(config.getExpireTime(), TimeUnit.MINUTES)
                .build();
        STAFF_URI = config.getStaffURI();
        CRM_URI = config.getCrmURI();
    }

    @Override
    public boolean authenticate(String username, String password) throws IOException, InterruptedException {
        if (SERVICE_NAMES.contains(username)) {
            return getServiceInfo(username, password);
        } else {
            return getInfoByRequestToStaff(username, password);
        }
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
                .setHeader("User", "ldap-service")
                .setHeader("X-Api-Key", bindPassword)
                .build();
    }

    private URI buildRequestURItoCRM(String username) {
        return URI.create(
                CRM_URI + String.format("?select=emailAddress" +
                        "&where[0][attribute]=userName" +
                        "&where[0][value]=%s" +
                        "&where[0][type]=equals", username)
        );
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
                .uri(URI.create(STAFF_URI))
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
    public Map<String, List<String>> getInfo(String username, String bindUser, String bindPassword) throws IOException, InterruptedException {
        var info = users.getIfPresent(username);

        if (info == null && SERVICE_NAMES.contains(bindUser)) {
            getInfoByRequestToCRM(username, bindPassword);
            info = users.getIfPresent(username);
        }

        return info;
    }

}