package dev.wilix.ldap.facade.server;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPSearchException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import dev.wilix.ldap.facade.espo.config.properties.EspoDataStorageConfigurationProperties;
import dev.wilix.ldap.facade.server.config.properties.ServerConfigurationProperties;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
public abstract class AbstractLDAPTest {

    @Autowired
    ServerConfigurationProperties config;
    @Autowired
    EspoDataStorageConfigurationProperties userStorageConfig;
    @MockBean
    HttpClient httpClient;
    @Mock
    HttpResponse<String> response;

    private final String SERVICE_BASE_DN = "ou=services,dc=wilix,dc=dev";
    private final String PEOPLE_BASE_DN = "ou=people,dc=wilix,dc=dev";

    protected final String TEST_SERVICE = "ldap-service";
    protected final String TEST_USER = "admin";
    private final String TEST_PASS = "ldap-test-password";

    private final String AUTH_URL = "/api/v1/App/user";
    private final String SERVICE_BIND_RESPONSE_BODY = "{\"user\":{\"id\":\"5fe06c48dc08fdb3d\",\"name\":\"ldap-service\",\"userName\":\"ldap-service\",\"emailAddress\":null}}";
    private final String USER_BIND_RESPONSE_BODY;
    private final String SERVICE_SEARCH_RESPONSE_BODY;


    public AbstractLDAPTest() {
        try {
            SERVICE_SEARCH_RESPONSE_BODY = getResourceAsString("service_search_response_body.json");
            USER_BIND_RESPONSE_BODY = getResourceAsString("user_bind_response_body.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getResourceAsString(String s) throws IOException {
        return Files.readString(Path.of(new ClassPathResource(s).getURI()));
    }
    // LDAP

    protected LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", config.getPort());
    }

    protected BindResult performBind(LDAPConnection ldap, String login) throws IOException, InterruptedException, LDAPException {
        return performBind(ldap, login, false);
    }

    protected BindResult performBind(LDAPConnection ldap, String login, boolean isServiceRequest) throws IOException, InterruptedException, LDAPException {
        setupHttpClientForBindResponse(isServiceRequest);

        return ldap.bind(login, TEST_PASS);
    }

    protected SearchResult performSearch(LDAPConnection ldap, SearchScope scope, String searchFilter, String... attributes) throws LDAPSearchException, IOException, InterruptedException {
        setupSuccessSearchRequestResponse();

        return ldap.search(PEOPLE_BASE_DN, scope, generateFilter(searchFilter), attributes);
    }

    // MOCKS

    // BIND

    protected void setupSuccessBindRequestResponse(boolean isServiceRequest) throws IOException, InterruptedException {
        setupHttpClientForBindResponse(isServiceRequest);
        setupSuccessBindResponseBody(isServiceRequest);
    }

    private void setupHttpClientForBindResponse(boolean isServiceRequest) throws IOException, InterruptedException {
        if (isServiceRequest) {
            when(httpClient.send(
                    buildServiceBindHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                    .thenReturn(response);
        } else {
            when(httpClient.send(
                    buildUserBindHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                    .thenReturn(response);
        }
    }

    protected void setupSuccessBindResponseBody(boolean isServiceRequest) {
        //Для успешного бинда нужно json тело иначе код будет OTHER_INT_VALUE (80)
        when(response.statusCode()).thenReturn(200);
        if (isServiceRequest) {
            when(response.body()).thenReturn(SERVICE_BIND_RESPONSE_BODY);
        } else {
            when(response.body()).thenReturn(USER_BIND_RESPONSE_BODY);
        }
    }

    //  SEARCH

    private void setupSuccessSearchRequestResponse() throws IOException, InterruptedException {
        setupHttpClientForSearchResponse();
        setupSuccessSearchResponseBody();
    }

    private void setupHttpClientForSearchResponse() throws IOException, InterruptedException {
        //search users
        when(httpClient.send(
                buildUsersSearchHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(response);
        // search groups
        HttpResponse<String> groupResponse = mock(HttpResponse.class);
        when(groupResponse.statusCode()).thenReturn(200);
        when(groupResponse.body()).thenReturn("{\"list\":[]}");
        when(httpClient.send(
                buildGroupsSearchHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(groupResponse);
    }

    private void setupSuccessSearchResponseBody() {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(SERVICE_SEARCH_RESPONSE_BODY);
    }

    // COMMON

    protected String generateServiceBindDN() {
        return String.format("uid=%s,%s", TEST_SERVICE, SERVICE_BASE_DN);
    }

    protected String generateUserBindDN() {
        return String.format("uid=%s,%s", TEST_USER, PEOPLE_BASE_DN);
    }

    private String generateFilter(String filter) {
        return String.format("(uid=%s)", filter);
    }

    protected void checkSearchResultEntryAttributes(SearchResultEntry entry, String... attributes) {
        for (String attributeName : attributes) {
            Attribute attribute = entry.getAttribute(attributeName);
            Assertions.assertNotNull(attribute.getValue());
        }
    }

    // HTTP

    //TODO методы дублируют код, в идеале вытягивать реализацию из CrmUserDataStorage
    private HttpRequest buildServiceBindHttpRequest() {
        return HttpRequest.newBuilder()
                .GET()
                .uri(getRequestURI())
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("X-Api-Key", TEST_PASS)
                .build();
    }

    private HttpRequest buildUserBindHttpRequest() {
        String base64Credentials = Base64.getEncoder().encodeToString((TEST_USER + ":" + TEST_PASS).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder()
                .GET()
                .uri(getRequestURI())
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("AUTHORIZATION", "Basic " + base64Credentials)
                .build();
    }

    private HttpRequest buildUsersSearchHttpRequest() {

        String uriTemplate;
        uriTemplate = userStorageConfig.getBaseUrl() + "/api/v1/User?select=emailAddress%2CteamsIds";

        URI uri = URI.create(uriTemplate);
        return HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("X-Api-Key", TEST_PASS).build();
    }

    private HttpRequest buildGroupsSearchHttpRequest() {

        String teamBaseUrl = userStorageConfig.getBaseUrl() + "/api/v1/Team";
        URI uri = URI.create(teamBaseUrl);
        return HttpRequest.newBuilder()
                .GET()
                .uri(uri)
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("X-Api-Key", TEST_PASS).build();
    }

    private URI getRequestURI() {
        return URI.create(userStorageConfig.getBaseUrl() + AUTH_URL);
    }
}