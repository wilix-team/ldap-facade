package dev.wilix.crm.ldap.model.crm;

import dev.wilix.crm.ldap.model.Authentication;

class UserAuthentication implements Authentication { // TODO toString
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
}
