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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import static org.mockito.Mockito.when;

public class SimpleTests extends AbstractLDAPTest {

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

        Assertions.assertThrows(LDAPException.class, () -> {
            try (LDAPConnection ldap = openLDAP()) {
                performBind(ldap, TEST_USER);
            }
        });

    }

    @Test
    public void bindAndSearchTest() throws InterruptedException, LDAPException, IOException {
        setupSuccessBindResponseBody(true);

        BindResult bindResult;
        SearchResult searchResult;

        try (LDAPConnection ldap = openLDAP()) {
            bindResult = performBind(ldap, generateServiceBindDN(), true);

            searchResult = performSearch(ldap, SearchScope.SUB, TEST_SERVICE);
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
    }

    @Test
    public void userBindTest() throws LDAPException, IOException, InterruptedException {
        setupSuccessBindResponseBody(false);

        BindResult bindResult;

        try (LDAPConnection ldap = openLDAP()) {
            bindResult = performBind(ldap, generateUserBindDN());
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
    }

}