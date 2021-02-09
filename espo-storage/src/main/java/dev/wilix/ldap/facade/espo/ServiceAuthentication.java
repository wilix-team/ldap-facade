package dev.wilix.ldap.facade.espo;


import dev.wilix.ldap.facade.api.Authentication;

/**
 * Результат аутентификации сервисного аккаунта в CRM.
 */
class ServiceAuthentication implements Authentication { // TODO toString
    private String serviceName;
    private String token;
    private boolean isSuccess;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }
}
