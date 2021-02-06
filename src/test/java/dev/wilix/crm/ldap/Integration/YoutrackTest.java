package dev.wilix.crm.ldap.Integration;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.LDAPTestUtils;
import dev.wilix.crm.ldap.AbstractLDAPTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class YoutrackTest extends AbstractLDAPTest {

    private final String[] attributes = {"uid", "cn", "mail", "memberof"};

    @Test
    public void whenServiceBindAndUserSearchAndBindSuccessThenOK() throws IOException, InterruptedException, LDAPException {
        BindResult serviceBindResult;
        SearchResult searchResult;
        BindResult userBindResult;

        try (LDAPConnection ldap = openLDAP()) {
            setupSuccessBindRequestResponse(true);
            serviceBindResult = performBind(ldap, generateServiceBindDN(), true);

            searchResult = performSearch(ldap, SearchScope.ONE, TEST_USER, attributes);

            setupSuccessBindRequestResponse(false);
            userBindResult = performBind(ldap, generateUserBindDN());
        }

        LDAPTestUtils.assertResultCodeEquals(serviceBindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
        searchResult.getSearchEntries().forEach(e -> checkSearchResultEntryAttributes(e, attributes));
        LDAPTestUtils.assertResultCodeEquals(userBindResult, ResultCode.SUCCESS);
    }

}