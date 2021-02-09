package dev.wilix.ldap.facade.server.Integration;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.LDAPTestUtils;
import dev.wilix.ldap.facade.server.AbstractLDAPTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class OpenVpnTest extends AbstractLDAPTest {

    @Test
    public void whenServiceBindAndUserSearchAndBindSuccessThenOK() throws IOException, InterruptedException, LDAPException {
        BindResult serviceBindResult;
        SearchResult searchResult;
        BindResult userBindResult;

        try (LDAPConnection ldap = openLDAP()) {
            setupSuccessBindRequestResponse(true);
            serviceBindResult = performBind(ldap, generateServiceBindDN(), true);

            searchResult = performSearch(ldap, SearchScope.SUB, TEST_USER);

            setupSuccessBindRequestResponse(false);
            userBindResult = performBind(ldap, generateUserBindDN());
        }

        LDAPTestUtils.assertResultCodeEquals(serviceBindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(userBindResult, ResultCode.SUCCESS);
    }

}