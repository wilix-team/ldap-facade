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

import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private final String USERNAME = "username";
    private final String USER_PASSWORD = "password";
    private final String SERVICE_NAME = "serviceName";
    private final String SERVICE_TOKEN = "token";
    private final String USER_BASE_DN = "ou=people,dc=example,dc=com";
    private final String GROUP_BASE_DN = "ou=groups,dc=example,dc=com";
    private final String SERVICE_BASE_DN = "ou=services,dc=example,dc=com";
    private final String[] ALL_USERS_ATTRIBUTES = {"company", "id", "entryuuid", "uid", "cn", "gn", "sn", "active", "telephoneNumber", "mail", "vcsName", "memberof"};
    private final String[] ALL_GROUPS_ATTRIBUTES = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn", "member"};

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
                BindResult bindResult = userBindResult(ldap, "wrongUsername", "wrongPassword");
            }
        });
    }

    @Test
    public void negativeServiceAuthenticate() {
        assertThrows(LDAPException.class, () -> {
            try (LDAPConnection ldap = openLDAP()) {
                BindResult bindResult = serviceBindResult(ldap, "wrongServiceName", "wrongToken");
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

        checkResultsOfRequest(4, bindResult, searchResult);
    }

    @Test
    public void searchAllEntityFromUsersDirectory() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, "(uid=*)");
        }

        checkResultsOfRequest(2, bindResult, searchResult);
    }

    @Test
    public void searchAllEntityFromGroupsDirectory() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(GROUP_BASE_DN, SearchScope.SUB, "(uid=*)");
        }

        checkResultsOfRequest(2, bindResult, searchResult);
    }

    @Test
    public void searchUserWhoParticipateInOneGroup() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(memberof=uid=groupTwo,ou=groups,dc=example,dc=com)"));
        }

        checkResultsOfRequest(1, bindResult, searchResult);
    }

    @Test
    public void searchUserByPattern() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(uid=u*e)"));
        }

        checkResultsOfRequest(1, bindResult, searchResult);
    }

    @Test
    public void searchUserWhoParticipateInAllTwoGroup() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(&(memberof=uid=groupOne,ou=groups,dc=example,dc=com)(memberof=uid=groupTwo,ou=groups,dc=example,dc=com))"));
        }

        checkResultsOfRequest(1, bindResult, searchResult);
    }

    @Test
    public void searchUserWhoIsNotActive() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(active=false)"));
        }

        checkResultsOfRequest(1, bindResult, searchResult);
    }

    @Test
    public void searchUserWithNotInitializeAttribute() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(!(notExistAttribute=*))"));
        }

        checkResultsOfRequest(2, bindResult, searchResult);
    }

    @Test
    public void searchUsersEntityFromRootDirectoryByTheirClassName() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(objectClass=organizationalPerson)");
        }

        checkResultsOfRequest(2, bindResult, searchResult);
    }

    @Test
    public void searchGroupsEntityFromRootDirectoryByTheirClassName() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(objectClass=groupOfNames)");
        }

        checkResultsOfRequest(2, bindResult, searchResult);
    }

    @Test
    public void searchGroupsEntityByCompleteMatcherTheirDn() throws LDAPException {
        BindResult bindResult;
        SearchResult searchResult;
        try (LDAPConnection ldap = openLDAP()) {
            bindResult = userBindResult(ldap);
            searchResult = ldap.search("dc=example,dc=com", SearchScope.SUB, "(dn=uid=groupOne,ou=groups,dc=example,dc=com)");
        }

        checkResultsOfRequest(1, bindResult, searchResult);
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", 636);
    }

    private BindResult userBindResult(LDAPConnection ldap) throws LDAPException {
        String usernameForLdap = String.format("uid=%s,%s", USERNAME, USER_BASE_DN);
        return ldap.bind(usernameForLdap, USER_PASSWORD);
    }

    private BindResult userBindResult(LDAPConnection ldap, String username, String password) throws LDAPException {
        String usernameForLdap = String.format("uid=%s,%s", username, USER_BASE_DN);
        return ldap.bind(usernameForLdap, password);
    }

    private BindResult serviceBindResult(LDAPConnection ldap) throws LDAPException {
        String serviceNameForLdap = String.format("uid=%s,%s", SERVICE_NAME, SERVICE_BASE_DN);
        return ldap.bind(serviceNameForLdap, SERVICE_TOKEN);
    }

    private BindResult serviceBindResult(LDAPConnection ldap, String service, String token) throws LDAPException {
        String serviceNameForLdap = String.format("uid=%s,%s", service, SERVICE_BASE_DN);
        return ldap.bind(serviceNameForLdap, token);
    }

    private void checkResultsOfRequest(int numberOfExpectReceiveEntries, BindResult bindResult, SearchResult searchResult) {
        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(searchResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertEntriesReturnedEquals(searchResult, numberOfExpectReceiveEntries);

        searchResult.getSearchEntries().forEach(searchEntry -> selectSuitableMethodCheckSearchResultAttributes(searchEntry, searchEntry.getAttribute("objectClass").getValue()));
    }

    private void selectSuitableMethodCheckSearchResultAttributes(SearchResultEntry entry, String objectClass) {
        if (objectClass.equals("organizationalPerson")) {
            checkSearchResultEntryAttributes(entry, ALL_USERS_ATTRIBUTES);
        } else if (objectClass.equals("groupOfNames")) {
            checkSearchResultEntryAttributes(entry, ALL_GROUPS_ATTRIBUTES);
        } else throw new IllegalStateException("Input entry is not a group or user.");
    }

    private void checkSearchResultEntryAttributes(SearchResultEntry entry, String[] allAttributes) {
        for (String attributeName : allAttributes) {
            Attribute attribute = entry.getAttribute(attributeName);
            Assertions.assertNotNull(attribute.getValue());
        }
    }
}
