package dev.wilix.ldap.facade.server;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.LDAPTestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.mockito.Mockito.when;

public class SimpleTests extends AbstractLDAPTest {

    @Test
    public void when401FromCRMThenException() {
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
    public void bindAndSearchByAdminCheckEmptyAttributesTest() throws InterruptedException, LDAPException, IOException {
        setupSuccessBindResponseBody(false);

        BindResult bindResult;
        SearchResult searchResult;
        String[] allAttributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "memberof", "vcsName"};

        try (LDAPConnection ldap = openLDAP()) {
            bindResult = performBind(ldap, generateServiceBindDN(), true);
            searchResult = performSearch(ldap, SearchScope.ONE, TEST_USER, allAttributes);
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
        searchResult.getSearchEntries().forEach(e -> checkSearchResultEntryAttributes(e, allAttributes));
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