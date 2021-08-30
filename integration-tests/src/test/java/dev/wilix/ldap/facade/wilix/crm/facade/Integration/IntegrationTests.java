package dev.wilix.ldap.facade.wilix.crm.facade.Integration;

import com.unboundid.ldap.sdk.*;
import com.unboundid.util.LDAPTestUtils;
import dev.wilix.ldap.facade.wilix.crm.facade.AbstractLDAPTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;

public class IntegrationTests extends AbstractLDAPTest {

    @ParameterizedTest(name = "{index} - {0}")
    @ArgumentsSource(TestCaseProvider.class)
    public void whenServiceBindAndUserSearchAndBindSuccessThenOK(String testCaseName, TestCase testCase) throws IOException, InterruptedException, LDAPException {
        BindResult serviceBindResult;
        SearchResult searchResult;
        BindResult userBindResult;
        String[] attributes = testCase.getAttributes();

        try (LDAPConnection ldap = openLDAP()) {
            setupSuccessBindRequestResponse(true);
            serviceBindResult = performBind(ldap, generateServiceBindDN(), true);

            searchResult = performSearch(ldap, SearchScope.SUB, TEST_USER, attributes);

            setupSuccessBindRequestResponse(false);
            userBindResult = performBind(ldap, generateUserBindDN());
        }

        LDAPTestUtils.assertResultCodeEquals(serviceBindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
        searchResult.getSearchEntries().forEach(e -> checkSearchResultEntryAttributes(e, attributes));
        LDAPTestUtils.assertResultCodeEquals(userBindResult, ResultCode.SUCCESS);
    }

}