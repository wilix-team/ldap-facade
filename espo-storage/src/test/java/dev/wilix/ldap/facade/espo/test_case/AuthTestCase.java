package dev.wilix.ldap.facade.espo.test_case;

/**
 * Authentication test cases.
 */
public class AuthTestCase extends TestCase {

    private boolean authSuccess;
    private Credentials authentication;

    public boolean isAuthSuccess() {
        return authSuccess;
    }

    public void setAuthSuccess(boolean authSuccess) {
        this.authSuccess = authSuccess;
    }

    public Credentials getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Credentials authentication) {
        this.authentication = authentication;
    }

    public static class Credentials {

        private String login;
        private String password;

        public String getLogin() {
            return login;
        }

        public void setLogin(String login) {
            this.login = login;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

    }

}