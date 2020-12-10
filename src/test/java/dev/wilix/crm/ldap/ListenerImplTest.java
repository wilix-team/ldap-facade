package dev.wilix.crm.ldap;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
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
    public void when200ThenSuccess() throws LDAPException, IOException, InterruptedException {
        //Для успешного бинда нужно json тело иначе код будет OTHER_INT_VALUE (80)
        when(response.body()).thenReturn("{\"result\":true,\"user\":{\"user_name\":\"admin\",\"first_name\":\"Администратор\",\"last_name\":\"Админ\",\"fullname\":\"Администратор Админ\",\"email\":\"admin@wilix.org\"}}");
        when(response.statusCode()).thenReturn(200);

        BindResult result = performDefaultBind();

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);
    }


    @Test
    public void when401thenException() throws IOException, InterruptedException {
        when(response.statusCode()).thenReturn(401);

        boolean exception = false;

        try {
            performDefaultBind();
        } catch (LDAPException e) {
            exception = true;
        }

        assertTrue(exception);
    }


    private BindResult performDefaultBind() throws IOException, InterruptedException, LDAPException {
        String loginPass = "admin";

        setupHttpClientForAuth(loginPass, loginPass);

        LDAPConnection ldap = openLDAP();
        BindResult result = bind(ldap, loginPass, loginPass);

        ldap.close();

        return result;
    }

    private void setupHttpClientForAuth(String username, String password) throws IOException, InterruptedException {
        when(httpClient.send(
                CrmUserDataStorage.buildHttpRequest(username, password), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(response);
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", appConfigurationProperties.getPort());
    }

    private BindResult bind(LDAPConnection ldap, String username, String password) throws LDAPException {
        return ldap.bind(String.format("uid=%s,ou=People,dc=wilix,dc=dev", username), password);
    }

}