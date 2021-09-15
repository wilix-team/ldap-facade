package dev.wilix.ldap.facade.espo.test_case;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Родитель для всех тестовых кейсов.
 * Содержит в себе поля свойственные для каждого типа кейсов.
 */
public abstract class TestCase {

    protected List<TestCaseRequest> requests;
    protected AccountType accountType;

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public List<TestCaseRequest> getRequests() {
        return requests;
    }

    public void setRequests(List<TestCaseRequest> requests) {
        this.requests = requests;
    }

    /**
     * Информация о запросе для заглушки в кейсе.
     */
    public static class TestCaseRequest {

        private String uri;
        private Map<String, String> params = new HashMap<>();
        private String responseFilePath;
        private String responseContentType = "application/json";
        private int responseStatusCode;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }

        public String getResponseFilePath() {
            return responseFilePath;
        }

        public void setResponseFilePath(String responseFilePath) {
            this.responseFilePath = responseFilePath;
        }

        public String getResponseContentType() {
            return responseContentType;
        }

        public void setResponseContentType(String responseContentType) {
            this.responseContentType = responseContentType;
        }

        public int getResponseStatusCode() {
            return responseStatusCode;
        }

        public void setResponseStatusCode(int responseStatusCode) {
            this.responseStatusCode = responseStatusCode;
        }

    }

    public enum AccountType {

        USER,
        SERVICE

    }

}