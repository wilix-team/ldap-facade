package dev.wilix.ldap.facade.espo.test_case;

/**
 * Предоставляет тестовые кейсы с запросом.
 */
public class RequestTestCaseProvider extends TestCaseProvider {

    private static final String caseDirectory = "tests/request";

    public RequestTestCaseProvider() {
        super(caseDirectory, RequestTestCase.class);
    }

}