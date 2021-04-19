package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;

/**
 * Класс для отображения успешной аутентификации пользователя из файла.
 */
class FileUserAuthentication implements Authentication {
    private String userName;
    private boolean isSuccess;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public String toString() {
        return "FileUserAuthentication{" +
                "userName='" + userName + '\'' +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
