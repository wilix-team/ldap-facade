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

    private BindResult bindResult;
    private SearchResult search;

    @Test
    public void positiveUserAuthenticate() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
    }

    @Test
    public void positiveServiceAuthenticate() {
        try (LDAPConnection ldap = openLDAP()) {
            serviceBindResult(ldap);
        } catch (LDAPException e) {
            e.printStackTrace();
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
    public void searchAllEntityFromRootDirectory() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search("dc=example,dc=com", SearchScope.SUB, "(uid=*)");
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(4);
    }

    @Test
    public void searchAllEntityFromUsersDirectory() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(USER_BASE_DN, SearchScope.SUB, "(uid=*)");
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(2);
    }

    @Test
    public void searchAllEntityFromGroupsDirectory() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(GROUP_BASE_DN, SearchScope.SUB, "(uid=*)");
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(2);
    }

    @Test
    public void searchUserWhoParticipateInOneGroup() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(memberof=uid=groupTwo,ou=groups,dc=example,dc=com)"));
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(1);
    }

    @Test
    public void searchUserByPattern() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(uid=u*e)"));
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(1);
    }

    @Test
    public void searchUserWhoParticipateInAllTwoGroup() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(&(memberof=uid=groupOne,ou=groups,dc=example,dc=com)(memberof=uid=groupTwo,ou=groups,dc=example,dc=com))"));
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(1);
    }

    @Test
    public void searchUserWhoIsNotActive() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(active=false)"));
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(1);
    }

    @Test
    public void searchUserWithNotInitializeAttribute() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search(USER_BASE_DN, SearchScope.SUB, ("(!(notExistAttribute=*))"));
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(2);
    }

    @Test
    public void searchUsersEntityFromRootDirectoryByTheirClassName() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search("dc=example,dc=com", SearchScope.SUB, "(objectClass=organizationalPerson)");
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(2);
    }

    @Test
    public void searchGroupsEntityFromRootDirectoryByTheirClassName() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search("dc=example,dc=com", SearchScope.SUB, "(objectClass=groupOfNames)");
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(2);
    }

    @Test
    public void searchGroupsEntityByCompleteMatcherTheirDn() {
        try (LDAPConnection ldap = openLDAP()) {
            userBindResult(ldap);
            search = ldap.search("dc=example,dc=com", SearchScope.SUB, "(dn=uid=groupOne,ou=groups,dc=example,dc=com)");
        } catch (LDAPException e) {
            e.printStackTrace();
        }

        checkResultsOfRequest(1);
    }

    private LDAPConnection openLDAP() throws LDAPException {
        return new LDAPConnection("localhost", 636);
    }

    private void userBindResult(LDAPConnection ldap) throws LDAPException {
        String usernameForLdap = String.format("uid=%s,%s", USERNAME, USER_BASE_DN);
        bindResult = ldap.bind(usernameForLdap, USER_PASSWORD);
    }

    private void userBindResult(LDAPConnection ldap, String username, String password) throws LDAPException {
        String usernameForLdap = String.format("uid=%s,%s", username, USER_BASE_DN);
        bindResult = ldap.bind(usernameForLdap, password);
    }

    private void serviceBindResult(LDAPConnection ldap) throws LDAPException {
        String serviceNameForLdap = String.format("uid=%s,%s", SERVICE_NAME, SERVICE_BASE_DN);
        bindResult = ldap.bind(serviceNameForLdap, SERVICE_TOKEN);
    }

    private void serviceBindResult(LDAPConnection ldap, String service, String token) throws LDAPException {
        String serviceNameForLdap = String.format("uid=%s,%s", service, SERVICE_BASE_DN);
        bindResult = ldap.bind(serviceNameForLdap, token);
    }

    private void checkResultsOfRequest(int numberOfExpectReceiveEntries) {
        LDAPTestUtils.assertResultCodeEquals(bindResult, ResultCode.SUCCESS);
        LDAPTestUtils.assertResultCodeEquals(search, ResultCode.SUCCESS);
        LDAPTestUtils.assertEntriesReturnedEquals(search, numberOfExpectReceiveEntries);

        search.getSearchEntries().forEach(searchEntry -> selectSuitableMethodCheckSearchResultAttributes(searchEntry, searchEntry.getAttribute("objectClass").getValue()));
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
