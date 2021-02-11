package dev.wilix.ldap.facade.espo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.espo.config.properties.EspoDataStorageConfigurationProperties;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Хранилище пользователей, построенное на основе Wilix CRM.
 */
public class EspoDataStorage implements DataStorage {
    // TODO Нужно добавить проверку у пользователей на флаг isActive
    private final static Logger LOG = LoggerFactory.getLogger(EspoDataStorage.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, Map<String, List<String>>> users;

    private final String searchUserByLoginUriTemplate;
    private final String searchUsersByPatternUriTemplate;
    private final String authenticateUserUri;

    public EspoDataStorage(HttpClient httpClient, ObjectMapper objectMapper, EspoDataStorageConfigurationProperties config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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
    public Map<String, List<String>> getSingleUserInfo(String userName, Authentication authentication) {

        if (!canSearch(authentication, userName)) {
            String message = String.format("Authentication %s can't access to search %s", authentication, userName);
            throw new IllegalStateException(message);
        }

        Map<String, List<String>> info = users.getIfPresent(userName);
        if (info == null) {
            info = performUsersSearch(searchUserByLoginUriTemplate, userName, authentication).get(0);
            if (!info.isEmpty()) {
                users.put(userName, info);
            }
        }
        return info;
    }

    @Override
    public List<Map<String, List<String>>> getUserInfoByTemplate(String template, Authentication authentication) {
        // '*' -> '%'(SQL syntax) -> '%25' (URL encoding)
        String encodedTemplate = template.replace("*", "%25");

        List<Map<String, List<String>>> foundedUsers = performUsersSearch(searchUsersByPatternUriTemplate, encodedTemplate, authentication);

        // Кладем в кэш.
        foundedUsers.forEach(user -> users.put(user.get("uid").get(0), user));

        return foundedUsers;
    }

    private boolean canSearch(Authentication authentication, String username) {
        if (authentication instanceof ServiceAuthentication) {
            return true;
        }
        if (authentication instanceof UserAuthentication) {
            return ((UserAuthentication) authentication).getUserName().equals(username);
        }

        LOG.error("Unknown authentication format.");
        throw new IllegalStateException("Unknown authentication format.");
    }

    private Map<String, List<String>> authenticateWithUserCredentials(String userName, String password) {
        HttpRequest.Builder requestBuilder = prepareAuthenticateRequest();

        final String base64Credentials = Base64.getEncoder().encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
        requestBuilder.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64Credentials);

        List<Map<String, List<String>>> users = requestUserInfo(requestBuilder.build(), responseNode -> responseNode.get("user"));
        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("Unexpected result from user authentication");
        }

        return users.get(0);
    }

    private Map<String, List<String>> authenticateWithServiceToken(String serviceName, String apiKey) {
        HttpRequest.Builder requestBuilder = prepareAuthenticateRequest();

        requestBuilder.setHeader("X-Api-Key", apiKey);

        List<Map<String, List<String>>> users = requestUserInfo(requestBuilder.build(), responseNode -> responseNode.get("user"));
        if (users == null || users.isEmpty()) {
            throw new IllegalStateException("Unexpected result from service authentication");
        }

        return users.get(0);
    }

    private HttpRequest.Builder prepareAuthenticateRequest() {
        return prepareBasicCrmRequest(authenticateUserUri);
    }

    private List<Map<String, List<String>>> performUsersSearch(String url, String userName, Authentication authentication) {

        HttpRequest.Builder request = prepareBasicCrmRequest(String.format(url, userName));
        if (authentication instanceof ServiceAuthentication) {
            String token = ((ServiceAuthentication) authentication).getToken();
            request.setHeader("X-Api-Key", token);
        } else if (authentication instanceof UserAuthentication) {
            String password = ((UserAuthentication) authentication).getPassword();
            String base64Credentials = Base64.getEncoder()
                    .encodeToString((userName + ":" + password).getBytes(StandardCharsets.UTF_8));
            request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + base64Credentials);
        }

        return requestUserInfo(request.build(), responseNode -> responseNode.get("list"));
    }

    private HttpRequest.Builder prepareBasicCrmRequest(String requestUri) {
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(requestUri))
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8");
    }

    private List<Map<String, List<String>>> requestUserInfo(HttpRequest request, Function<JsonNode, JsonNode> userNodeExtractor) {
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

            // FIXME Почините меня, мне плохо.
            if (userJson.isArray()) {
                return StreamSupport.stream(userJson.spliterator(), false)
                        .map(this::parseUserInfoInCrmFormatFromJsonNode)
                        .collect(Collectors.toList());
            } else {
                return List.of(parseUserInfoInCrmFormatFromJsonNode(userJson));
            }

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

            // TODO Нужно формировать display name.
            jsonToUserFieldSetter.accept("id", value -> info.put("id", List.of(value)));
            jsonToUserFieldSetter.accept("userName", value -> info.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));
            jsonToUserFieldSetter.accept("emailAddress", value -> info.put("mail", List.of(value)));

            List<String> memberOfList = new ArrayList<>();
            JsonNode teamsNamesNode = userJsonField.get("teamsNames");
            if (teamsNamesNode != null) {
                teamsNamesNode.elements()
                        .forEachRemaining((teamNameNode) -> memberOfList.add(teamNameNode.textValue()));
            }
            info.put("memberof", memberOfList);

            // атрибут для имени в vcs системах (git)
            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            info.put("vcsName", vcsName);
        }

        return info;
    }
}