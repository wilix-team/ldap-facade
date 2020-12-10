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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
public class ListenerImplTest {

    @Autowired
    AppConfigurationProperties appConfigurationProperties;
    @MockBean
    HttpClient httpClient;

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
    public void success() throws LDAPException, IOException, InterruptedException {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"result\":true,\"user\":{\"user_name\":\"admin\",\"first_name\":\"Администратор\",\"last_name\":\"Админ\",\"fullname\":\"Администратор Админ\",\"email\":\"admin@wilix.org\"}}");

        when(httpClient.send(
                CrmUserDataStorage.buildHttpRequest("admin", "admin"), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(response);

        LDAPConnection connection = new LDAPConnection("localhost", appConfigurationProperties.getPort());
        BindResult result = connection.bind("uid=admin,ou=People,dc=wilix,dc=dev", "admin");

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);

        connection.close();
    }

    @Test
    public void testWrongAuth() throws LDAPException, IOException, InterruptedException {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);

        when(httpClient.send(
                CrmUserDataStorage.buildHttpRequest("admin", "admin"), HttpResponse.BodyHandlers.ofString()))
                .thenReturn(response);

        LDAPConnection connection = new LDAPConnection("localhost", appConfigurationProperties.getPort());
        BindResult result = connection.bind("uid=admin,ou=People,dc=wilix,dc=dev", "admin");

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.INVALID_CREDENTIALS_INT_VALUE);
    }

    private HttpResponse mock401Response() {
        HttpResponse response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(401);

        return response;
    }

}