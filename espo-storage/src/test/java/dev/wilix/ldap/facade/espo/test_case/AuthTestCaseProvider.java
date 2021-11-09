package dev.wilix.ldap.facade.espo.test_case;

/**
 * Represents authentication tests.
 */
public class AuthTestCaseProvider extends TestCaseProvider {

    private static final String caseDirectory = "tests/auth";

    public AuthTestCaseProvider() {
        super(caseDirectory, AuthTestCase.class);
    }

}