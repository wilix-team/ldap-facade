package dev.wilix.ldap.facade.espo;


import dev.wilix.ldap.facade.api.Authentication;

import java.util.Objects;

/**
 * Результат аутентификации сервисного аккаунта в CRM.
 */
class ServiceAuthentication implements Authentication {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceAuthentication that = (ServiceAuthentication) o;
        return serviceName.equals(that.serviceName) && token.equals(that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, token);
    }

    @Override
    public String toString() {
        return "ServiceAuthentication{" +
                "serviceName='" + serviceName + '\'' +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
