package dev.wilix.ldap.facade.espo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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

@SpringBootTest(classes = TestApplication.class)
public class WiremockEspoTest {

    private final String USERS_AND_GROUPS_INFO = "users_and_groups_info.json";
    private final String USER_INFO = "user_info.json";
    private final String USERS_URI = "/api/v1/User";
    private final String USERS_SEARCH_INFO_URI = "/api/v1/User?select=emailAddress%2CteamsIds";
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
    public void positiveAuthenticateUserCheck() {
        stubMappingUser(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationUser = espoDataStorage.authenticateUser("username", "password");
        assertTrue(authenticationUser.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void positiveAuthenticateServiceCheck() {
        stubMappingService(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationService = espoDataStorage.authenticateService("serviceName", "token");
        assertTrue(authenticationService.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticationUserCheck() {
        stubMappingUser(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationUser = espoDataStorage.authenticateUser("wrongUsername", "wrongPassword");
        assertFalse(authenticationUser.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateServiceCheck() {
        stubMappingService(USERS_AUTH_URI, USER_INFO, SC_OK);
        Authentication authenticationService = espoDataStorage.authenticateService("serviceName", "wrongToken");
        assertFalse(authenticationService.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateUserWithNotExistFileCheck() {
        stubMappingUser(USERS_AUTH_URI, "", SC_OK);
        Authentication authentication = espoDataStorage.authenticateUser("username", "password");
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateServiceWithNotExistFileCheck() {
        stubMappingService(USERS_AUTH_URI, "", SC_OK);
        Authentication authentication = espoDataStorage.authenticateService("serviceName", "token");
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateUserWithWrongUriCheck() {
        stubMappingUser(WRONG_URI, USER_INFO, SC_OK);
        Authentication authentication = espoDataStorage.authenticateUser("username", "password");
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateServiceWithWrongUriCheck() {
        stubMappingService(WRONG_URI, USER_INFO, SC_OK);
        Authentication authentication = espoDataStorage.authenticateService("serviceName", "token");
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void positiveReceiveUsersInfoWithUserAuth() {
        stubMappingUser(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(receiveUserAuthentication());
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "gn", "sn", "active", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void positiveReceiveUsersInfoWithServiceAuth() {
        stubMappingService(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(receiveServiceAuthentication());
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "gn", "sn", "active", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void positiveReceiveGroupsInfoWithUserAuth() {
        stubMappingUser(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        stubMappingUser(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(receiveUserAuthentication());
        String[] attributes = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(2, GROUPS_URI);
    }

    @Test
    public void positiveReceiveGroupsInfoWithServiceAuth() {
        stubMappingService(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        stubMappingService(USERS_URI, USERS_AND_GROUPS_INFO, SC_OK);
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(receiveServiceAuthentication());
        String[] attributes = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(2, GROUPS_URI);
    }

    @Test
    public void negativeReceiveUsersInfoBecauseWrongUserAuth() {
        stubMappingUser(USERS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllUsers(receiveUserAuthentication()));
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void negativeReceiveUsersInfoBecauseWrongServiceAuth() {
        stubMappingService(USERS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllUsers(receiveServiceAuthentication()));
        wireMockServer.verify(1, allRequests());
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void negativeReceiveGroupsInfoBecauseWrongUserAuth() {
        stubMappingUser(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllGroups(receiveUserAuthentication()));
        checkRequest(1, GROUPS_URI);
    }

    @Test
    public void negativeReceiveGroupsInfoBecauseWrongServiceAuth() {
        stubMappingService(GROUPS_URI, USERS_AND_GROUPS_INFO, SC_FORBIDDEN);
        assertThrows(UncheckedExecutionException.class, () -> espoDataStorage.getAllGroups(receiveServiceAuthentication()));
        checkRequest(1, GROUPS_URI);
    }

    private void stubMappingUser(String uri, String jsonFilePath, int status) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBodyFile(jsonFilePath);
        
        wireMockServer.addStubMapping(WireMock.get(urlPathEqualTo(uri))
                .withBasicAuth("username", "password")
                .willReturn(responseDefBuilder
                ).build());


    }

    private void stubMappingService(String uri, String jsonFilePath, int status) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBodyFile(jsonFilePath);
        
        wireMockServer.addStubMapping(WireMock.get(urlPathEqualTo(uri))
                .withHeader("X-Api-Key", equalTo("token"))
                .willReturn(responseDefBuilder
                ).build());
    }

    private void checkRequest(int numberOfRequest, String uri) {
        wireMockServer.verify(numberOfRequest, allRequests());
        wireMockServer.verify(getRequestedFor(urlEqualTo(uri))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8")));
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
