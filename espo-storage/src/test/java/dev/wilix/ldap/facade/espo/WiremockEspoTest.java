package dev.wilix.ldap.facade.espo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.wilix.ldap.facade.api.Authentication;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.allRequests;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
public class WiremockEspoTest {

    public static final String DEFAULT_USER_NAME = "username";
    public static final String DEFAULT_USER_PASSWORD = "password";
    public static final String DEFAULT_SERVICE_NAME = "serviceName";
    public static final String DEFAULT_SERVICE_TOKEN = "token";

    private final String USERS_INFO_BODY_FILE_PATH = "users_info.json";
    private final String GROUPS_INFO_BODY_FILE_PATH = "groups_info.json";
    private final String USER_INFO_SUCCESS_BODY_FILE_PATH = "user_info.json";
    private final String BROKEN_JSON_BODY_FILE_PATH = "broken_format.json";

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
        WireMockConfiguration config = wireMockConfig()
                .port(8080)
                .fileSource(new SingleRootFileSource("src/test/resources/wiremock"));

        wireMockServer = new WireMockServer(config);
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
        stubMappingWithUserAuth(USERS_AUTH_URI, USER_INFO_SUCCESS_BODY_FILE_PATH, SC_OK);
        Authentication authenticationUser = espoDataStorage.authenticateUser(DEFAULT_USER_NAME, DEFAULT_USER_PASSWORD);
        assertTrue(authenticationUser.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void positiveAuthenticateServiceCheck() {
        stubMappingWithServiceAuth(USERS_AUTH_URI, USER_INFO_SUCCESS_BODY_FILE_PATH, SC_OK);
        Authentication authenticationService = espoDataStorage.authenticateService(DEFAULT_SERVICE_NAME, DEFAULT_SERVICE_TOKEN);
        assertTrue(authenticationService.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticationUserCheck() {
        stubMappingWithUserAuth(USERS_AUTH_URI, USER_INFO_SUCCESS_BODY_FILE_PATH, HttpStatus.FORBIDDEN_403, "wrongUsername", "wrongPassword");
        Authentication authenticationUser = espoDataStorage.authenticateUser("wrongUsername", "wrongPassword");
        assertFalse(authenticationUser.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateServiceCheck() {
        stubMappingWithServiceAuth(USERS_AUTH_URI, USER_INFO_SUCCESS_BODY_FILE_PATH, HttpStatus.FORBIDDEN_403, "wrongServiceToken");
        Authentication authenticationService = espoDataStorage.authenticateService(DEFAULT_SERVICE_NAME, "wrongServiceToken");
        assertFalse(authenticationService.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateUserWithBrokenJsonFormat() {
        stubMappingWithUserAuth(USERS_AUTH_URI, BROKEN_JSON_BODY_FILE_PATH, SC_OK);
        Authentication authentication = espoDataStorage.authenticateUser(DEFAULT_USER_NAME, DEFAULT_USER_PASSWORD);
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateServiceWithBrokenJsonFormat() {
        stubMappingWithServiceAuth(USERS_AUTH_URI, BROKEN_JSON_BODY_FILE_PATH, SC_OK);
        Authentication authentication = espoDataStorage.authenticateService(DEFAULT_SERVICE_NAME, DEFAULT_SERVICE_TOKEN);
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateUserWithWrongUriCheck() {
        stubMappingWithUserAuth(WRONG_URI, USER_INFO_SUCCESS_BODY_FILE_PATH, SC_OK);
        Authentication authentication = espoDataStorage.authenticateUser(DEFAULT_USER_NAME, DEFAULT_USER_PASSWORD);
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void negativeAuthenticateServiceWithWrongUriCheck() {
        stubMappingWithServiceAuth(WRONG_URI, USER_INFO_SUCCESS_BODY_FILE_PATH, SC_OK);
        Authentication authentication = espoDataStorage.authenticateService(DEFAULT_SERVICE_NAME, DEFAULT_SERVICE_TOKEN);
        assertFalse(authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    @Test
    public void positiveReceiveUsersInfoWithUserAuth() {
        stubMappingWithUserAuth(USERS_URI, USERS_INFO_BODY_FILE_PATH, SC_OK);
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(buildUserAuthentication());
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "gn", "sn", "active", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void positiveReceiveUsersInfoWithServiceAuth() {
        stubMappingWithServiceAuth(USERS_URI, USERS_INFO_BODY_FILE_PATH, SC_OK);
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(buildServiceAuthentication());
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "gn", "sn", "active", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void positiveReceiveGroupsInfoWithUserAuth() {
        stubMappingWithUserAuth(GROUPS_URI, GROUPS_INFO_BODY_FILE_PATH, SC_OK);
        stubMappingWithUserAuth(USERS_URI, USERS_INFO_BODY_FILE_PATH, SC_OK);
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(buildUserAuthentication());
        String[] attributes = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(2, GROUPS_URI);
    }

    @Test
    public void positiveReceiveGroupsInfoWithServiceAuth() {
        stubMappingWithServiceAuth(GROUPS_URI, GROUPS_INFO_BODY_FILE_PATH, SC_OK);
        stubMappingWithServiceAuth(USERS_URI, USERS_INFO_BODY_FILE_PATH, SC_OK);
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(buildServiceAuthentication());
        String[] attributes = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(2, GROUPS_URI);
    }

    @Test
    public void negativeReceiveUsersInfoBecauseWrongUserAuth() {
        stubMappingWithUserAuth(USERS_URI, USERS_INFO_BODY_FILE_PATH, SC_FORBIDDEN);
        assertThrows(RuntimeException.class, () -> espoDataStorage.getAllUsers(buildUserAuthentication()));
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void negativeReceiveUsersInfoBecauseWrongServiceAuth() {
        stubMappingWithServiceAuth(USERS_URI, USERS_INFO_BODY_FILE_PATH, SC_FORBIDDEN);
        assertThrows(RuntimeException.class, () -> espoDataStorage.getAllUsers(buildServiceAuthentication()));
        wireMockServer.verify(1, allRequests());
        checkRequest(1, USERS_SEARCH_INFO_URI);
    }

    @Test
    public void negativeReceiveGroupsInfoBecauseWrongUserAuth() {
        stubMappingWithUserAuth(GROUPS_URI, GROUPS_INFO_BODY_FILE_PATH, SC_FORBIDDEN);
        assertThrows(RuntimeException.class, () -> espoDataStorage.getAllGroups(buildUserAuthentication()));
        checkRequest(1, GROUPS_URI);
    }

    @Test
    public void negativeReceiveGroupsInfoBecauseWrongServiceAuth() {
        stubMappingWithServiceAuth(GROUPS_URI, GROUPS_INFO_BODY_FILE_PATH, SC_FORBIDDEN);
        assertThrows(RuntimeException.class, () -> espoDataStorage.getAllGroups(buildServiceAuthentication()));
        checkRequest(1, GROUPS_URI);
    }

    private void stubMappingWithUserAuth(String uri, String jsonFilePath, int status) {
        stubMappingWithUserAuth(uri, jsonFilePath, status, DEFAULT_USER_NAME, DEFAULT_USER_PASSWORD);
    }

    private void stubMappingWithUserAuth(String uri, String jsonFilePath, int status, String userName, String password) {
        ResponseDefinitionBuilder mockResponseBuilder = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBodyFile(jsonFilePath);

        wireMockServer.addStubMapping(WireMock.get(urlPathEqualTo(uri))
                .withBasicAuth(userName, password)
                .willReturn(mockResponseBuilder)
                .build());
    }

    private void stubMappingWithServiceAuth(String uri, String jsonFilePath, int status) {
        stubMappingWithServiceAuth(uri, jsonFilePath, status, DEFAULT_SERVICE_TOKEN);
    }

    private void stubMappingWithServiceAuth(String uri, String jsonFilePath, int status, String serviceToken) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBodyFile(jsonFilePath);

        wireMockServer.addStubMapping(WireMock.get(urlPathEqualTo(uri))
                .withHeader("X-Api-Key", equalTo(serviceToken))
                .willReturn(responseDefBuilder)
                .build());
    }

    private void checkRequest(int numberOfRequest, String uri) {
        wireMockServer.verify(numberOfRequest, allRequests());
        wireMockServer.verify(getRequestedFor(urlEqualTo(uri))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8")));
    }

    private Authentication buildUserAuthentication() {
        UserAuthentication userAuthentication = new UserAuthentication();
        userAuthentication.setUserName(DEFAULT_USER_NAME);
        userAuthentication.setPassword(DEFAULT_USER_PASSWORD);
        return userAuthentication;
    }

    private Authentication buildServiceAuthentication() {
        ServiceAuthentication serviceAuthentication = new ServiceAuthentication();
        serviceAuthentication.setServiceName(DEFAULT_SERVICE_NAME);
        serviceAuthentication.setToken(DEFAULT_SERVICE_TOKEN);
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
