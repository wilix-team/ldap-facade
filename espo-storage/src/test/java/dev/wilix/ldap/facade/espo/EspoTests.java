package dev.wilix.ldap.facade.espo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.SingleRootFileSource;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.HttpStatus;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.espo.test_case.*;
import dev.wilix.ldap.facade.espo.test_case.RequestTestCase.Target;
import dev.wilix.ldap.facade.espo.test_case.TestCase.TestCaseRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static dev.wilix.ldap.facade.espo.test_case.TestCase.AccountType.SERVICE;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
public class EspoTests {

    private static final UserAuthentication USER_AUTHENTICATION = new UserAuthentication("username", "password", false);
    private static final ServiceAuthentication SERVICE_AUTHENTICATION = new ServiceAuthentication("serviceName", "token", false);

    private static final String GROUPS_URI = "/api/v1/Team";
    private static final String USERS_URI = "/api/v1/User";
    private static final String USERS_AUTH_URI = "/api/v1/App/user";

    private static WireMockServer wireMockServer;

    @Autowired
    private EspoDataStorage espoDataStorage;

    @BeforeAll
    public static void beforeAll() {
        SingleRootFileSource woremockFileSource = new SingleRootFileSource(EspoTests.class.getClassLoader().getResource("wiremock").getPath());
        wireMockServer = new WireMockServer(8080, woremockFileSource, true);
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
        // Prepare auth mock
        stubRequest(testCase.getRequests().get(0), prepareTestCaseExpectedAuthentication(testCase));

        // Do auth
        Authentication authentication = authenticate(testCase.getAccountType(), testCase.getAuthentication().getLogin(), testCase.getAuthentication().getPassword());

        // Check auth
        assertEquals(testCase.isAuthSuccess(), authentication.isSuccess());
        checkRequest(1, USERS_AUTH_URI);
    }

    private Authentication prepareTestCaseExpectedAuthentication(AuthTestCase testCase) {
        switch (testCase.getAccountType()) {
            case USER:
                return new UserAuthentication(testCase.getAuthentication().getLogin(), testCase.getAuthentication().getPassword(), false);
            case SERVICE:
                return new ServiceAuthentication(testCase.getAuthentication().getLogin(), testCase.getAuthentication().getPassword(), false);
            default:
                throw new IllegalStateException("Unexpected authentication type value: " + testCase.getAccountType());
        }
    }

    private Authentication authenticate(TestCase.AccountType accountType, String login, String password) {
        switch (accountType) {
            case USER:
                return espoDataStorage.authenticateUser(login, password);
            case SERVICE:
                return espoDataStorage.authenticateService(login, password);
            default:
                throw new IllegalStateException("Unexpected authentication type value: " + accountType);
        }
    }

    @ParameterizedTest(name = "{index} - {0}")
    @ArgumentsSource(RequestTestCaseProvider.class)
    public void requestTests(String testCaseName, RequestTestCase testCase) {
        Authentication authentication = testCase.getAccountType() == SERVICE ? SERVICE_AUTHENTICATION : USER_AUTHENTICATION;

        for (TestCaseRequest testCaseRequest : testCase.getRequests()) {
            stubRequest(testCaseRequest, authentication);
        }

        boolean isPositiveTestCase = testCase.getRequests().stream()
                .map(TestCaseRequest::getResponseStatusCode)
                .allMatch(HttpStatus::isSuccess);

        if (isPositiveTestCase) {
            getAndCheckEntities(testCase.getTarget(), authentication, testCase.getAttributes());
        } else {
            assertThrows(RuntimeException.class, () -> getAndCheckEntities(testCase.getTarget(), authentication));
        }
    }

    private void getAndCheckEntities(Target target, Authentication authentication, String... attributes) {
        switch (target) {
            case USERS:
                List<Map<String, List<String>>> usersInfo = espoDataStorage.getAllUsers(authentication);
                usersInfo.forEach(e -> checkAttributes(e, attributes));
                checkRequest(1, USERS_URI);
                break;
            case GROUPS:
                List<Map<String, List<String>>> groupsInfo = espoDataStorage.getAllGroups(authentication);
                groupsInfo.forEach(e -> checkAttributes(e, attributes));
                checkRequest(1, USERS_URI);
                checkRequest(1, GROUPS_URI);
                break;
            default:
                throw new UnsupportedOperationException("Unknown receive target");
        }
    }

    private void stubRequest(TestCaseRequest testCaseRequest, Authentication expectedAuthentication) {
        ResponseDefinitionBuilder responseDefBuilder = aResponse()
                .withStatus(testCaseRequest.getResponseStatusCode())
                .withHeader(ContentTypeHeader.KEY, testCaseRequest.getResponseContentType())
                .withBodyFile(testCaseRequest.getResponseFilePath());

        MappingBuilder mappingBuilder = WireMock.get(urlPathEqualTo(testCaseRequest.getUri()))
                .willReturn(responseDefBuilder);

        testCaseRequest.getParams().forEach((k, v) -> mappingBuilder.withQueryParam(k, equalTo(v)));

        wireMockServer.addStubMapping(buildStubWithAuthCredentials(mappingBuilder, expectedAuthentication));
    }

    private StubMapping buildStubWithAuthCredentials(MappingBuilder mappingBuilder, Authentication expectedAuthentication) {
        if (expectedAuthentication instanceof UserAuthentication) {
            UserAuthentication userAuthentication = (UserAuthentication) expectedAuthentication;
            mappingBuilder.withBasicAuth(userAuthentication.getUserName(), userAuthentication.getPassword());
        } else {
            ServiceAuthentication serviceAuthentication = (ServiceAuthentication) expectedAuthentication;
            mappingBuilder.withHeader("X-Api-Key", equalTo(serviceAuthentication.getToken()));
        }
        return mappingBuilder.build();
    }

    private void checkRequest(int numberOfRequest, String uri) {
        RequestPatternBuilder requestPattern = getRequestedFor(urlPathEqualTo(uri));
        wireMockServer.verify(numberOfRequest, requestPattern);
    }

    private void checkAttributes(Map<String, List<String>> e, String[] attributes) {
        Arrays.stream(attributes).map(e::get)
                .forEach(values -> {
                    assertNotNull(values);
                    assertFalse(values.isEmpty());
                });
    }

}