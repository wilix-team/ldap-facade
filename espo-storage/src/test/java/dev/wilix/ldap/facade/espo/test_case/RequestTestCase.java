package dev.wilix.ldap.facade.espo.test_case;

/**
 * Тестовый кейс с запросом.
 */
public class RequestTestCase extends TestCase {

    private String target;
    private String[] attributes;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String[] getAttributes() {
        return attributes;
    }

    public void setAttributes(String[] attributes) {
        this.attributes = attributes;
    }

}