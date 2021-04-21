package dev.wilix.ldap.facade.server;

import com.unboundid.ldap.sdk.*;
import com.unboundid.util.LDAPTestUtils;
import dev.wilix.ldap.facade.api.DataStorage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static dev.wilix.ldap.facade.server.TestUtils.*;

@SpringBootTest
@ActiveProfiles("test")
public class ServerTest {

    @TestConfiguration
    public static class Configuration {
        @Bean
        public DataStorage testStorage() {
            return new TestStorage();
        }
    }

    @Test
    public void positiveUserAuthenticate() throws LDAPException {
        BindResult bindResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
    }

    @Test
    public void positiveServiceAuthenticate() throws LDAPException {
        BindResult bindResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = serviceBindResult(ldap);
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
    }

    @Test
    public void negativeUserAuthenticate() {
        assertThrows(LDAPException.class, () -> {
            try (LDAPConnection ldap = openLDAP()) {
                userBindResult(ldap, "wrongUsername", "wrongPassword");
            }
        });
    }

    @Test
    public void negativeServiceAuthenticate() {
        assertThrows(LDAPException.class, () -> {
            try (LDAPConnection ldap = openLDAP()) {
                serviceBindResult(ldap, "wrongServiceName", "wrongToken");
            }
        });
    }

    @Test
    public void searchAllEntityFromRootDirectory() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(uid=*)");
        }

        checkSearchResults(4, bindResult, searchResult);
    }

    @Test
    public void searchAllEntityFromUsersDirectory() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, "(uid=*)");
        }

        checkSearchResults(2, bindResult, searchResult);
    }

    @Test
    public void searchAllEntityFromGroupsDirectory() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(GROUP_BASE_DN, SearchScope.SUB, "(uid=*)");
        }

        checkSearchResults(2, bindResult, searchResult);
    }

    @Test
    public void searchUserWhoParticipateInOneGroup() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(memberof=uid=groupTwo,ou=groups,dc=example,dc=com)"));
        }

        checkSearchResults(1, bindResult, searchResult);
    }

    @Test
    public void searchUserByPattern() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(uid=u*e)"));
        }

        checkSearchResults(1, bindResult, searchResult);
    }

    @Test
    public void searchUserWhoParticipateInAllTwoGroup() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(&(memberof=uid=groupOne,ou=groups,dc=example,dc=com)(memberof=uid=groupTwo,ou=groups,dc=example,dc=com))"));
        }

        checkSearchResults(1, bindResult, searchResult);
    }

    @Test
    public void searchNotActiveUser() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(active=false)"));
        }

        checkSearchResults(1, bindResult, searchResult);
    }

    @Test
    public void searchUserByNotExistAttribute() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(!(notExistAttribute=*))"));
        }

        checkSearchResults(2, bindResult, searchResult);
    }

    @Test
    public void searchUsersEntityFromRootDirectoryByTheirClassName() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(objectClass=organizationalPerson)");
        }

        checkSearchResults(2, bindResult, searchResult);
    }

    @Test
    public void searchGroupsEntityFromRootDirectoryByTheirClassName() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(objectClass=groupOfNames)");
        }

        checkSearchResults(2, bindResult, searchResult);
    }

    @Test
    public void searchGroupsEntityByCompleteMatcherTheirDn() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(dn=uid=groupOne,ou=groups,dc=example,dc=com)");
        }

        checkSearchResults(1, bindResult, searchResult);
    }

    @Test
    public void searchAllEntityWithoutAuthentication() {
        try (LDAPConnection ldap = openLDAP()) {
            ldap.search("dc=example,dc=com", SearchScope.SUB, "(uid=*)");
        } catch (LDAPException e) {
            assertEquals(49, e.getResultCode().intValue());
            assertEquals("Incorrect credentials or access rights.", e.getDiagnosticMessage());
        }
    }

    @Test
    public void searchUserInOneAndNoInOtherGroup() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(&(memberof=uid=groupOne,ou=groups,dc=example,dc=com)(!(memberof=uid=groupTwo,ou=groups,dc=example,dc=com)))"));
        }

        checkSearchResults(1, bindResult, searchResult);
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", 636);
    }

    private BindResult userBindResult(LDAPConnection ldap) throws LDAPException {
        return bind(ldap, USERNAME, USER_PASSWORD, USER_BASE_DN);
    }

    private void userBindResult(LDAPConnection ldap, String username, String password) throws LDAPException {
        bind(ldap, username, password, USER_BASE_DN);
    }

    private BindResult serviceBindResult(LDAPConnection ldap) throws LDAPException {
        return bind(ldap, NAME_OF_SERVICE, TOKEN_OF_SERVICE, SERVICE_BASE_DN);
    }

    private void serviceBindResult(LDAPConnection ldap, String service, String token) throws LDAPException {
        bind(ldap, service, token, SERVICE_BASE_DN);
    }

    private BindResult bind(LDAPConnection ldap, String username, String password, String baseDn) throws LDAPException {
        String usernameForLdap = String.format("uid=%s,%s", username, baseDn);
        return ldap.bind(usernameForLdap, password);
    }

    private void checkSearchResults(int numberOfExpectReceiveEntries, BindResult bindResult, SearchResult searchResult) {
        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertEntriesReturnedEquals(searchResult, numberOfExpectReceiveEntries);

        searchResult.getSearchEntries().forEach(searchEntry -> checkResultEntity(searchEntry, searchEntry.getAttribute("objectClass").getValue()));
    }

    private void checkResultEntity(SearchResultEntry entry, String objectClass) {
        switch (objectClass) {
            case "organizationalPerson":
                checkSearchResultEntryAttributes(entry, ALL_USERS_ATTRIBUTES);
                break;
            case "groupOfNames":
                checkSearchResultEntryAttributes(entry, ALL_GROUPS_ATTRIBUTES);
                break;
            default:
                throw new IllegalStateException("Unknown entry class.");
        }
    }

    private void checkSearchResultEntryAttributes(SearchResultEntry entry, String[] allAttributes) {
        for (String attributeName : allAttributes) {
            Attribute attribute = entry.getAttribute(attributeName);
            Assertions.assertNotNull(attribute.getValue());
        }
    }
}
