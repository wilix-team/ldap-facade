package dev.wilix.ldap.facade.espo.test_case;

import java.util.List;

/**
 * Родитель для всех тестовых кейсов.
 * Содержит в себе поля свойственные для каждого типа кейсов.
 */
public abstract class TestCase {

    protected List<TestCaseRequest> requests;
    protected boolean serviceAccount;

    public List<TestCaseRequest> getRequests() {
        return requests;
    }

    public void setRequests(List<TestCaseRequest> requests) {
        this.requests = requests;
    }

    public boolean isServiceAccount() {
        return serviceAccount;
    }

    public void setServiceAccount(boolean serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    /**
     * Информация о запросе для заглушки в кейсе.
     */
    public static class TestCaseRequest {

        private String uri;
        private String responseFilePath;
        private int responseStatusCode;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getResponseFilePath() {
            return responseFilePath;
        }

        public void setResponseFilePath(String responseFilePath) {
            this.responseFilePath = responseFilePath;
        }

        public int getResponseStatusCode() {
            return responseStatusCode;
        }

        public void setResponseStatusCode(int responseStatusCode) {
            this.responseStatusCode = responseStatusCode;
        }

    }

}