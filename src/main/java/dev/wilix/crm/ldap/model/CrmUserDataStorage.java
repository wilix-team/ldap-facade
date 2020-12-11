package dev.wilix.crm.ldap.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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

    public CrmUserDataStorage(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    // TODO Настройка
    private static final String STAFF_PORTAL_SSO_URI = "https://staff.wilix.org/red/api/sso/authenticate";


    // TODO Сделать время хранения настройкой.
    private final Cache<String, Map<String, List<String>>> users = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build();

    //TODO Возможно просматривать какой-нибудь флаг заблокированности пользователя.
    @Override
    public boolean authenticate(String username, String password) throws IOException, InterruptedException {
        HttpRequest crmRequest = buildHttpRequest(username, password);

        LOG.info("Send authenticate request to CRM for user: {}", username);

        HttpResponse<String> response = httpClient.send(crmRequest, HttpResponse.BodyHandlers.ofString());

        LOG.info("Receive response from CRM: {}", response);

        if (response.statusCode() != 200) {
            LOG.warn("For username {} receive bad response from CRM {}", username, response);
            return false;
        }

        saveUser(username, response);

        return true;
    }

    private void saveUser(String username, HttpResponse<String> response) throws JsonProcessingException {
        Map<String, List<String>> userInfo = parseUserInfo(response.body());
        LOG.info("Successfully parse user from CRM: {}", userInfo);

        users.put(username, userInfo);
    }

    public static HttpRequest buildHttpRequest(String username, String password) {
        return HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(username, password)))
                .uri(URI.create(STAFF_PORTAL_SSO_URI))
                .setHeader("User-Agent", "Ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .build();
    }

    private static String generateRequestBody(String username, String password) {
        return String.format("{\"username\": \"%1$s\",\"password\": \"%2$s\"}", username, password);
    }

    /**
     * Парсинг атрибутов пользователя из CRM формата в LDAP формат.
     */
    private Map<String, List<String>> parseUserInfo(String crmResponseBody) throws JsonProcessingException {
        Map<String, List<String>> userInfo = new HashMap<>();

        JsonNode responseNode = objectMapper.readTree(crmResponseBody);

        userInfo.put("company", List.of("WILIX"));

        JsonNode userJsonField = responseNode.get("user");
        if (userJsonField != null) {
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = userJsonField.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            jsonToUserFieldSetter.accept("user_name", value -> userInfo.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("fullname", value -> userInfo.put("cn", List.of(value)));
            jsonToUserFieldSetter.accept("email", value -> userInfo.put("mail", List.of(value)));

            // атрибут для имени в vcs системах (git)
            // youtrack несколько имен понимает через символ перевода каретки. Подстраиваемся под этот формат.
            userInfo.put("vcsName", Collections.singletonList(""));
            StringBuilder vcsName = new StringBuilder();
            jsonToUserFieldSetter.accept("user_name", value -> vcsName.append(value));
            jsonToUserFieldSetter.accept("email", value -> vcsName.append("\n").append(value));
            userInfo.put("vcsName", Collections.singletonList(vcsName.toString()));

            // TODO Группы!!!
        }

        return userInfo;
    }

    /**
     * Пользователь уже был получен и распаршен во время аутентификации.
     * Просто получаем его.
     */
    @Override
    public Map<String, List<String>> getUserInfo(String userName) {
        return users.getIfPresent(userName);
    }

}