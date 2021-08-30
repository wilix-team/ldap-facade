package dev.wilix.ldap.facade.espo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.espo.test_case.AuthTestCase;
import dev.wilix.ldap.facade.espo.test_case.AuthTestCaseProvider;
import dev.wilix.ldap.facade.espo.test_case.RequestTestCase;
import dev.wilix.ldap.facade.espo.test_case.RequestTestCaseProvider;
import dev.wilix.ldap.facade.espo.test_case.TestCase.TestCaseRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
public class EspoTests {

    private static final UserAuthentication USER_AUTHENTICATION = new UserAuthentication("username", "password", false);
    private static final ServiceAuthentication SERVICE_AUTHENTICATION = new ServiceAuthentication("serviceName", "token", false);

    public static final String POSITIVE_TEST_CASE_PREFIX = "positive_";
    private static final String AVATAR_FILE_PATH = "avatar.png";

    private static final String GROUPS_URI = "/api/v1/Team";
    private static final String USERS_URI = "/api/v1/User";
    private static final String USERS_AUTH_URI = "/api/v1/App/user";

    private static WireMockServer wireMockServer;

    @Autowired
    private EspoDataStorage espoDataStorage;

    @BeforeAll
    public static void beforeAll() {
        wireMockServer = new WireMockServer(8080, new SingleRootFileSource(EspoTests.class.getClassLoader().getResource("wiremock").getPath()), true);
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

    @ParameterizedTest(name = "{index} - {0}")
    @ArgumentsSource(AuthTestCaseProvider.class)
    public void authTests(String testCaseName, AuthTestCase testCase) {
        Authentication authentication = prepareAuthentication(testCase);

        for (TestCaseRequest testCaseRequest : testCase.getRequests()) {
            stubRequest(testCaseRequest.getUri(), testCaseRequest.getResponseFilePath(), testCaseRequest.getResponseStatusCode(), authentication);
        }

        authentication = authenticate(authentication);

        assertEquals(testCase.isAuthSuccess(), authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    private Authentication prepareAuthentication(AuthTestCase testCase) {
        if (testCase.getAuthentication() != null) {
            return testCase.isServiceAccount() ?
                    new ServiceAuthentication(testCase.getAuthentication().getLogin(), testCase.getAuthentication().getPassword(), false) :
                    new UserAuthentication(testCase.getAuthentication().getLogin(), testCase.getAuthentication().getPassword(), false);
        } else {
            return testCase.isServiceAccount() ? SERVICE_AUTHENTICATION : USER_AUTHENTICATION;
        }
    }

    private Authentication authenticate(Authentication authentication) {
        if (authentication instanceof UserAuthentication) {
            UserAuthentication userAuthentication = (UserAuthentication) authentication;
            return espoDataStorage.authenticateUser(userAuthentication.getUserName(), userAuthentication.getPassword());
        } else if (authentication instanceof ServiceAuthentication) {
            ServiceAuthentication serviceAuthentication = (ServiceAuthentication) authentication;
            return espoDataStorage.authenticateService(serviceAuthentication.getServiceName(), serviceAuthentication.getToken());
        } else {
            throw new UnsupportedOperationException("Unknown authentication type");
        }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @ArgumentsSource(RequestTestCaseProvider.class)
    public void requestTests(String testCaseName, RequestTestCase testCase) {
        Authentication authentication = testCase.isServiceAccount() ? SERVICE_AUTHENTICATION : USER_AUTHENTICATION;

        for (TestCaseRequest testCaseRequest : testCase.getRequests()) {
            stubRequest(testCaseRequest.getUri(), testCaseRequest.getResponseFilePath(), testCaseRequest.getResponseStatusCode(), authentication);
        }

        if (testCaseName.startsWith(POSITIVE_TEST_CASE_PREFIX)) {
            stubAvatarRequest(authentication);
            receiveTarget(testCase.getTarget(), authentication, testCase.getAttributes());
        } else {
            assertThrows(RuntimeException.class, () -> receiveTarget(testCase.getTarget(), authentication));
        }
    }

    private void receiveTarget(String target, Authentication authentication, String... attributes) {
        switch (target) {
            case "users":
                receiveUsers(authentication, attributes);
                break;
            case "groups":
                receiveGroups(authentication, attributes);
                break;
            default:
                throw new UnsupportedOperationException("Unknown receive target");
        }

    }

    private void receiveUsers(Authentication authentication, String[] attributes) {
        List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(authentication);
        usersInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(1, USERS_URI);
    }

    private void receiveGroups(Authentication authentication, String[] attributes) {
        List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(authentication);
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
        checkRequest(1, USERS_URI);
        checkRequest(1, GROUPS_URI);
    }

    private void stubRequest(String uri, String responseFilePath, int responseStatusCode, Authentication authentication) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(responseStatusCode)
                .withHeader("Content-Type", "application/json")
                .withBodyFile(responseFilePath);

        MappingBuilder mappingBuilder = WireMock.get(urlPathEqualTo(uri))
                .willReturn(responseDefBuilder);

        wireMockServer.addStubMapping(buildStubWithAuthCredentials(mappingBuilder, authentication));
    }

    private StubMapping buildStubWithAuthCredentials(MappingBuilder mappingBuilder, Authentication authentication) {
        if (authentication instanceof UserAuthentication) {
            UserAuthentication userAuthentication = (UserAuthentication) authentication;
            mappingBuilder.withBasicAuth(userAuthentication.getUserName(), userAuthentication.getPassword());
        } else {
            ServiceAuthentication serviceAuthentication = (ServiceAuthentication) authentication;
            mappingBuilder.withHeader("X-Api-Key", equalTo(serviceAuthentication.getToken()));
        }
        return mappingBuilder.build();
    }

    private void stubAvatarRequest(Authentication authentication) {
        ResponseDefinitionBuilder responseDefinitionBuilder = aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/png")
                .withBodyFile(AVATAR_FILE_PATH);

        MappingBuilder mappingBuilder = WireMock.get(urlPathEqualTo("/"))
                .withQueryParam("entryPoint", equalTo("avatar"))
                .willReturn(responseDefinitionBuilder);

        wireMockServer.addStubMapping(buildStubWithAuthCredentials(mappingBuilder, authentication));
    }

    private void checkRequest(int numberOfRequest, String uri) {
        RequestPatternBuilder requestPattern = getRequestedFor(urlPathEqualTo(uri))
                .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"));
        wireMockServer.verify(numberOfRequest, requestPattern);
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