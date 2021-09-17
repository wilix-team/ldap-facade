package dev.wilix.ldap.facade.espo.test_case;

/**
 * Тестовый кейс с запросом.
 */
public class RequestTestCase extends TestCase {

    private Target target;
    private String[] attributes;

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public String[] getAttributes() {
        return attributes;
    }

    public void setAttributes(String[] attributes) {
        this.attributes = attributes;
    }

    public enum Target {
        USERS,
        GROUPS
    }

}