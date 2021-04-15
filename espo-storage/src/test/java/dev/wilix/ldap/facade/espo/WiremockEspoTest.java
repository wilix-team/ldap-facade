package dev.wilix.ldap.facade.espo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.util.concurrent.UncheckedExecutionException;
import dev.wilix.ldap.facade.api.Authentication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.allRequests;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = TestApplication.class, webEnvironment = RANDOM_PORT)
public class WiremockEspoTest {

    private final String USERS_AND_GROUPS_INFO = "users_and_groups_info.json";
    private final String USER_INFO = "user_info.json";
    private final String NOT_EXIST_FILE = "not_exist.json";
    private final String USERS_URI = "/api/v1/User";
    private final String GROUPS_URI = "/api/v1/Team";
    private final String USERS_AUTH_URI = "/api/v1/App/user";
    private final String WRONG_URI = "/api/v1/wrong";

    private static WireMockServer wireMockServer;

    @Autowired
    EspoDataStorage espoDataStorage;

    @BeforeAll
    public static void beforeAll() {

        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
    }

    @BeforeEach
    public void beforeEach() {
        wireMockServer.resetAll();
    }

    @AfterAll
    public static void afterAll() {
        wireMockServer.stop();
    }

    @Test
    public void authenticateUserCheck() {
        stubMappingUserAuth(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationUser = espoDataStorage.authenticateUser("username", "password");
        assertTrue(authenticationUser.isSuccess());
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void authenticateServiceCheck() {
        stubMappingServiceAuth(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationService = espoDataStorage.authenticateService("serviceName", "token");
        assertTrue(authenticationService.isSuccess());
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void wrongAuthenticationUserCheck() {
        stubMappingUserAuth(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationUser = espoDataStorage.authenticateUser("wrongUsername", "wrongPassword");
        assertFalse(authenticationUser.isSuccess());
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void wrongAuthenticateServiceCheck() {
        stubMappingServiceAuth(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationService = espoDataStorage.authenticateService("serviceName", "wrongToken");
        assertFalse(authenticationService.isSuccess());
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void authenticateUserWithNotExistFileCheck() {
        stubMappingUserAuth(USERS_AUTH_URI, NOT_EXIST_FILE, SC_OK);
        Authentication authentication = espoDataStorage.authenticateUser("username", "password");
        assertFalse(authentication.isSuccess());
        wireMockServer.verify(0, allRequests());
    }

    @Test
    public void authenticateServiceWithNotExistFileCheck() {
        stubMappingServiceAuth(USERS_AUTH_URI, NOT_EXIST_FILE, SC_OK);
        Authentication authentication = espoDataStorage.authenticateService("serviceName", "token");
        assertFalse(authentication.isSuccess());
        wireMockServer.verify(0, allRequests());
    }

    @Test
    public void authenticateUserWithWrongUriCheck() {
        stubMappingUserAuth(WRONG_URI, USER_INFO, SC_OK);
        Authentication authentication = espoDataStorage.authenticateUser("username", "password");
        assertFalse(authentication.isSuccess());
    }

    @Test
    public void authenticateServiceWithWrongUriCheck() {
        stubMappingServiceAuth(WRONG_URI, USER_INFO, SC_OK);
        Authentication authentication = espoDataStorage.authenticateService("serviceName", "token");
        assertFalse(authentication.isSuccess());
    }

    @Test
    public void receiveUsersInfoWithUserAuth() {
        stubMappingUserAuth(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(receiveUserAuthentication());
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "gn", "sn", "active", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void receiveUsersInfoWithServiceAuth() {
        stubMappingServiceAuth(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(receiveServiceAuthentication());
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "gn", "sn", "active", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void receiveGroupsInfoWithUserAuth() {
        stubMappingUserAuth(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        stubMappingUserAuth(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(receiveUserAuthentication());
        String[] attributes = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        wireMockServer.verify(2, allRequests());
    }

    @Test
    public void receiveGroupsInfoWithServiceAuth() {
        stubMappingServiceAuth(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        stubMappingServiceAuth(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(receiveServiceAuthentication());
        String[] attributes = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        wireMockServer.verify(2, allRequests());
    }

    @Test
    public void wrongReceiveUsersInfoBecauseWrongUserAuth() {
        stubMappingUserAuth(USERS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllUsers(receiveUserAuthentication()));
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void wrongReceiveUsersInfoBecauseWrongServiceAuth() {
        stubMappingServiceAuth(USERS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllUsers(receiveServiceAuthentication()));
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void wrongReceiveGroupsInfoBecauseWrongUserAuth() {
        stubMappingUserAuth(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllGroups(receiveUserAuthentication()));
        wireMockServer.verify(1, allRequests());
    }

    @Test
    public void wrongReceiveGroupsInfoBecauseWrongServiceAuth() {
        stubMappingServiceAuth(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllGroups(receiveServiceAuthentication()));
        wireMockServer.verify(1, allRequests());
    }

    private void stubMappingUserAuth(String uri, String jsonFile, int status) {
        wireMockServer.addStubMapping(WireMock.get(urlPathEqualTo(uri))
                .withBasicAuth("username", "password")
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile(jsonFile)
                ).build());
    }

    private void stubMappingServiceAuth(String uri, String jsonFile, int status) {
        wireMockServer.addStubMapping(WireMock.get(urlPathEqualTo(uri))
                .withHeader("X-Api-Key", equalTo("token"))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile(jsonFile)
                ).build());
    }

    private Authentication receiveUserAuthentication() {
        UserAuthentication userAuthentication = new UserAuthentication();
        userAuthentication.setUserName("username");
        userAuthentication.setPassword("password");
        return userAuthentication;
    }

    private Authentication receiveServiceAuthentication() {
        ServiceAuthentication serviceAuthentication = new ServiceAuthentication();
        serviceAuthentication.setServiceName("serviceName");
        serviceAuthentication.setToken("token");
        return serviceAuthentication;
    }

    private void checkAttributes(Map<String, List<String>> e, String[] attributes) {
        for (String attributeName : attributes) {
            if (attributeName.equals("memberof")) {
                assertFalse(e.get(attributeName).isEmpty());
            } else {
                assertNotNull(e.get(attributeName));
            }
        }
    }

}
