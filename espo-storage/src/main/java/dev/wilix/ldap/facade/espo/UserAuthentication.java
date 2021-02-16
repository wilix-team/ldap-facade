package dev.wilix.ldap.facade.espo;

import dev.wilix.ldap.facade.api.Authentication;

import java.util.Objects;

class UserAuthentication implements Authentication {
    private String userName;
    private String password;
    private boolean isSuccess;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
        UserAuthentication that = (UserAuthentication) o;
        return userName.equals(that.userName) && password.equals(that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, password);
    }

    @Override
    public String toString() {
        return "UserAuthentication{" +
                "userName='" + userName + '\'' +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
