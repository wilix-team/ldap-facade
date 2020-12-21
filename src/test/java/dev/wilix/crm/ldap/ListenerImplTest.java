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
import dev.wilix.crm.ldap.config.AppConfigurationProperties;
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
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
public class ListenerImplTest {

    @Autowired
    AppConfigurationProperties config;
    @MockBean
    HttpClient httpClient;
    @Mock
    HttpResponse<String> response;

    private final String BASE_DN = "ou=People,dc=wilix,dc=dev";

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

        try {
            performBind("admin", "admin");
        } catch (LDAPException e) {
            exception = true;
        }

        assertTrue(exception);
    }

    @Test
    public void bindAndSearchTest() throws InterruptedException, LDAPException, IOException {
        String loginPass = "ldap-service";
        setupSuccessResponseMock();

        LDAPConnection ldap = openLDAP();

        BindResult bindResult = performBind(loginPass, config.getCrmToken());

        SearchResult searchResult = ldap.search(BASE_DN, SearchScope.SUB, generateFilter("maxim.yanchuk"));

        ldap.close();

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
    }

    private String generateFilter(String loginPass) {
        return String.format("(uid=%s)", loginPass);
    }

    private BindResult performBind(String login, String password) throws IOException, InterruptedException, LDAPException {
        setupHttpClientForAuth();

        BindResult result = null;

        try (LDAPConnection ldap = openLDAP()) {
            result = bind(ldap, login, password);
        }

        return result;
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", config.getPort());
    }

    private BindResult bind(LDAPConnection ldap, String userDN, String password) throws LDAPException {
        return ldap.bind(userDN, password);
    }

    public HttpRequest buildApplicationHttpRequest() {
        return HttpRequest.newBuilder()
                .GET()
                .uri(generateApplicationRequestURI("maxim.yanchuk"))
                .setHeader("User", "ldap-service")
                .setHeader("X-Api-Key", config.getCrmToken())
                .build();
    }

    private URI generateApplicationRequestURI(String username) {
        return URI.create(
                config.getCrmURI() + String.format("?select=emailAddress" +
                        "&where[0][attribute]=userName" +
                        "&where[0][value]=%s" +
                        "&where[0][type]=equals", username)
        );
    }

    // MOCKS

    private void setupHttpClientForAuth() throws IOException, InterruptedException {
        when(httpClient.send(
                buildApplicationHttpRequest(), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(response);
    }

    private void setupSuccessResponseMock() {
        //Для успешного бинда нужно json тело иначе код будет OTHER_INT_VALUE (80)
        when(response.body()).thenReturn("{\"total\":1,\"list\":[{\"id\":\"5f7ad9914f960a46f\",\"name\":\"\\u042f\\u043d\\u0447\\u0443\\u043a \\u041c\\u0430\\u043a\\u0441\\u0438\\u043c\",\"isAdmin\":false,\"userName\":\"maxim.yanchuk\",\"type\":\"regular\",\"salutationName\":\"\",\"firstName\":\"\\u041c\\u0430\\u043a\\u0441\\u0438\\u043c\",\"lastName\":\"\\u042f\\u043d\\u0447\\u0443\\u043a\",\"isActive\":true,\"isPortalUser\":false,\"emailAddress\":\"maxim.yanchuk@wilix.org\",\"middleName\":\"\\u041f\\u0435\\u0442\\u0440\\u043e\\u0432\\u0438\\u0447\",\"createdById\":\"1\"}]}");
        when(response.statusCode()).thenReturn(200);
    }

}