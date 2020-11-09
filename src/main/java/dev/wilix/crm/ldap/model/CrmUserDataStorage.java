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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Храниилище пользователей, построенное на основе Wilix CRM.
 *
 * TODO Возможно прсматривать какой-нибудь флаг заблокированности пользователя.
 */
public class CrmUserDataStorage implements UserDataStorage {

    private static Logger LOG = LoggerFactory.getLogger(CrmUserDataStorage.class);

    // TODO Настройка
    private static final String STAFF_PORTAL_SSO_URI = "https://staff.wilix.org/red/api/sso/authenticate";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // TODO Сделать время хранения настройкой.
    private final Cache<String, Map<String, List<String>>> users = CacheBuilder.newBuilder()
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .build();

    @Override
    public boolean authenticate(String username, String password) throws IOException, InterruptedException {
        HttpRequest crmRequest = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(generateRequestBody(username, password)))
                .uri(URI.create(STAFF_PORTAL_SSO_URI))
                .setHeader("User-Agent", "Ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .build();

        LOG.info("Send authenticate request to CRM for user: {}", username);

        // FIXME В документации написано, что его можно много раз использовать,
        //  но на деле после первого запроса все остальные виснут.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(crmRequest, HttpResponse.BodyHandlers.ofString());

        LOG.info("Receive response from CRM: {}", response);

        if (response.statusCode() != 200) {
            LOG.warn("For username {} receive bad response from CRM {}", username, response);
            return false;
        }

        Map<String, List<String>> userInfo = parseUserInfo(response.body());
        LOG.info("Successfully parse user from CRM: {}", userInfo);

        users.put(username, userInfo);

        return true;
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
            // TODO Группы!!!
            // TODO VCS names для корректной интеграции с VCS.
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
