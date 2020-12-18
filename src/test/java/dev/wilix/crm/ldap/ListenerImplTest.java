package dev.wilix.crm.ldap;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchRequest;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.LDAPTestUtils;
import com.unboundid.util.ssl.JVMDefaultTrustManager;
import com.unboundid.util.ssl.SSLUtil;
import dev.wilix.crm.ldap.config.AppConfigurationProperties;
import dev.wilix.crm.ldap.model.CrmUserDataStorage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
public class ListenerImplTest {

    @Autowired
    AppConfigurationProperties appConfigurationProperties;
    @MockBean
    HttpClient httpClient;
    @Mock
    HttpResponse<String> response;

    @Test
    @Disabled("Нужно доработать тесты, моки и т.д.")
    public void disabled() throws LDAPException, GeneralSecurityException {
        String serverKeyStorePath = Path.of("C:\\Users\\Van\\sandbox\\certs\\fck.keystore").toFile().getAbsolutePath();
//        final SSLUtil serverSSLUtil = new SSLUtil(null, new TrustStoreTrustManager(serverKeyStorePath));
        final SSLUtil serverSSLUtil = new SSLUtil(null, JVMDefaultTrustManager.getInstance());
//        final SSLUtil serverSSLUtil = new SSLUtil(null, new TrustAllTrustManager());

        LDAPConnection connection = new LDAPConnection(serverSSLUtil.createSSLSocketFactory("TLSv1.2"), "fckapp.s2.wilix.dev",
                appConfigurationProperties.getPort());

        // TODO Переделать на мокирование информации и пользвателе.
        BindResult result = connection.bind("uid=stanislav.melnichuk,ou=People,dc=wilix,dc=dev", "FIXME");

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);

        connection.close();
    }

    @Test
    public void whenUserWithoutDCThenSuccess() throws InterruptedException, LDAPException, IOException {
        setupSuccessResponseMock();

        BindResult result = performDefaultBind(false);

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);
    }

    @Test
    public void whenUserWithDCThenSuccess() throws InterruptedException, LDAPException, IOException {
        setupSuccessResponseMock();

        BindResult result = performDefaultBind(true);

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);
    }

    @Test
    public void when401FromCRMThenException() throws IOException, InterruptedException {
        when(response.statusCode()).thenReturn(401);

        boolean exception = false;

        try {
            performDefaultBind(false);
        } catch (LDAPException e) {
            exception = true;
        }

        assertTrue(exception);
    }

    @Test
    public void bindAndSearchTest() throws InterruptedException, LDAPException, IOException {
        String loginPass = "admin";
        setupSuccessResponseMock();
        setupHttpClientForAuth(loginPass, loginPass);

        LDAPConnection ldap = openLDAP();

        bind(ldap, userDN(loginPass), loginPass);
        SearchResult result = ldap.search(SearchRequest.ALL_USER_ATTRIBUTES, SearchScope.BASE, userUID(loginPass));

        ldap.close();

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);
    }

    private BindResult performDefaultBind(boolean withDC) throws IOException, InterruptedException, LDAPException {
        String loginPass = "admin";
        setupHttpClientForAuth(loginPass, loginPass);

        BindResult result = null;

        try (LDAPConnection ldap = openLDAP()) {
            result = withDC ? bind(ldap, userDNWithDC(loginPass), loginPass)
                    : bind(ldap, userDN(loginPass), loginPass);
        }

        return result;
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", appConfigurationProperties.getPort());
    }

    private BindResult bind(LDAPConnection ldap, String userDN, String password) throws LDAPException {
        return ldap.bind(userDN, password);
    }

    private String userDNWithDC(String username) {
        return String.format("uid=%s,ou=People,dc=wilix,dc=dev", username);
    }

    private String userDN(String username) {
        return String.format("uid=%s,ou=People", username);
    }

    private String userUID(String loginPass) {
        return String.format("uid=%s", loginPass);
    }

    // MOCKS

    private void setupHttpClientForAuth(String username, String password) throws IOException, InterruptedException {
        when(httpClient.send(
                CrmUserDataStorage.buildHttpRequest(username, password), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(response);
    }

    private void setupSuccessResponseMock() {
        //Для успешного бинда нужно json тело иначе код будет OTHER_INT_VALUE (80)
        when(response.body()).thenReturn("{\"result\":true,\"user\":{\"user_name\":\"admin\",\"first_name\":\"Администратор\",\"last_name\":\"Админ\",\"fullname\":\"Администратор Админ\",\"email\":\"admin@wilix.org\"}}");
        when(response.statusCode()).thenReturn(200);
    }

}