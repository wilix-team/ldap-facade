package dev.wilix.crm.ldap;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.LDAPTestUtils;
import com.unboundid.util.ssl.JVMDefaultTrustManager;
import com.unboundid.util.ssl.SSLUtil;
import dev.wilix.crm.ldap.config.properties.AppConfigurationProperties;
import dev.wilix.crm.ldap.config.properties.UserDataStorageConfigurationProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
public class ListenerImplTest {

    @Autowired
    AppConfigurationProperties config;
    @Autowired
    UserDataStorageConfigurationProperties userStorageConfig;
    @MockBean
    HttpClient httpClient;
    @Mock
    HttpResponse<String> response;

    private final String SERVICE_BASE_DN = "ou=Services,dc=wilix,dc=ru";
    private final String PEOPLE_BASE_DN = "ou=People,dc=wilix,dc=ru";

    private final String TEST_SERVICE = "ldap-service";
    private final String TEST_USER = "admin";
    private final String TEST_PASS = "ldap-test-password";


    private final String AUTH_URL = "/api/v1/App/user";

    @Test
    @Disabled("Нужно доработать тесты, моки и т.д.")
    public void disabled() throws LDAPException, GeneralSecurityException {
        String serverKeyStorePath = Path.of("C:\\Users\\Van\\sandbox\\certs\\fck.keystore").toFile().getAbsolutePath();
//        final SSLUtil serverSSLUtil = new SSLUtil(null, new TrustStoreTrustManager(serverKeyStorePath));
        final SSLUtil serverSSLUtil = new SSLUtil(null, JVMDefaultTrustManager.getInstance());
//        final SSLUtil serverSSLUtil = new SSLUtil(null, new TrustAllTrustManager());

        LDAPConnection connection = new LDAPConnection(serverSSLUtil.createSSLSocketFactory("TLSv1.2"), "fckapp.s2.wilix.dev",
                config.getPort());

        // TODO Переделать на мокирование информации и пользвателе.
        BindResult result = connection.bind("uid=stanislav.melnichuk,ou=People,dc=wilix,dc=dev", "FIXME");

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);

        connection.close();
    }

    @Test
    public void when401FromCRMThenException() throws IOException, InterruptedException {
        when(response.statusCode()).thenReturn(401);

        boolean exception = false;

        try (LDAPConnection ldap = openLDAP()) {
            performBind(ldap, TEST_USER);
        } catch (LDAPException e) {
            exception = true;
        }

        assertTrue(exception);
    }

    @Test
    public void bindAndSearchTest() throws InterruptedException, LDAPException, IOException {
        setupSuccessResponse();

        BindResult bindResult;
        SearchResult searchResult;

        try (LDAPConnection ldap = openLDAP()) {
            bindResult = performBind(ldap, generateServiceBindDN(TEST_SERVICE), true);

            searchResult = ldap.search(PEOPLE_BASE_DN, SearchScope.SUB, generateFilter(TEST_SERVICE));
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
    }

    @Test
    public void userBindTest() throws LDAPException, IOException, InterruptedException {
        setupSuccessResponse();

        BindResult bindResult;

        try (LDAPConnection ldap = openLDAP()) {
            bindResult = performBind(ldap, generateUserBindDN(TEST_USER));
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", config.getPort());
    }

    private BindResult performBind(LDAPConnection ldap, String login) throws IOException, InterruptedException, LDAPException {
        return performBind(ldap, login, false);
    }

    private BindResult performBind(LDAPConnection ldap, String login, boolean isServiceRequest) throws IOException, InterruptedException, LDAPException {
        setupHttpClientForResponse(isServiceRequest);

        return ldap.bind(login, TEST_PASS);
    }

    // MOCKS

    private void setupHttpClientForResponse(boolean isServiceRequest) throws IOException, InterruptedException {
        if (isServiceRequest) {
            when(httpClient.send(
                    buildServiceHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                    .thenReturn(response);
        } else {
            when(httpClient.send(
                    buildUserHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                    .thenReturn(response);
        }
    }

    private void setupSuccessResponse() {
        //Для успешного бинда нужно json тело иначе код будет OTHER_INT_VALUE (80)
        when(response.body()).thenReturn("{\"total\":1,\"list\":[{\"id\":\"5f7ad9914f960a46f\",\"name\":\"\\u042f\\u043d\\u0447\\u0443\\u043a \\u041c\\u0430\\u043a\\u0441\\u0438\\u043c\",\"isAdmin\":false,\"userName\":\"maxim.yanchuk\",\"type\":\"regular\",\"salutationName\":\"\",\"firstName\":\"\\u041c\\u0430\\u043a\\u0441\\u0438\\u043c\",\"lastName\":\"\\u042f\\u043d\\u0447\\u0443\\u043a\",\"isActive\":true,\"isPortalUser\":false,\"emailAddress\":\"maxim.yanchuk@wilix.org\",\"middleName\":\"\\u041f\\u0435\\u0442\\u0440\\u043e\\u0432\\u0438\\u0447\",\"createdById\":\"1\"}]}");
        when(response.statusCode()).thenReturn(200);
    }

    // COMMON

    private String generateServiceBindDN(String serviceName) {
        return String.format("uid=%s,%s", serviceName, SERVICE_BASE_DN);
    }

    private String generateUserBindDN(String username) {
        return String.format("uid=%s,%s", username, PEOPLE_BASE_DN);
    }

    private String generateFilter(String loginPass) {
        return String.format("(uid=%s)", loginPass);
    }

    public HttpRequest buildServiceHttpRequest() {
        return HttpRequest.newBuilder()
                .GET()
                .uri(getRequestURI())
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("X-Api-Key", TEST_PASS)
                .build();
    }

    public HttpRequest buildUserHttpRequest() {
        String base64Credentials = Base64.getEncoder().encodeToString((TEST_USER + ":" + TEST_PASS).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder()
                .GET()
                .uri(getRequestURI())
                .setHeader("User-Agent", "ldap-facade")
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setHeader("AUTHORIZATION", "Basic " + base64Credentials)
                .build();
    }

    private URI getRequestURI() {
        return URI.create(userStorageConfig.getBaseUrl() + AUTH_URL);
    }

}