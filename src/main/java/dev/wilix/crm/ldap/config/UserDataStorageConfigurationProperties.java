package dev.wilix.crm.ldap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated // TODO Добавить зависимость и навесить валидацию для полей.
@ConfigurationProperties(prefix = "storage.user")
public class UserDataStorageConfigurationProperties {
    private int cacheExpirationMinutes = 2;
    private String userDirectAuthUri;
    private String appUserSearchUri;

    public int getCacheExpirationMinutes() {
        return cacheExpirationMinutes;
    }

    public void setCacheExpirationMinutes(int cacheExpirationMinutes) {
        this.cacheExpirationMinutes = cacheExpirationMinutes;
    }

    public String getUserDirectAuthUri() {
        return userDirectAuthUri;
    }

    public void setUserDirectAuthUri(String userDirectAuthUri) {
        this.userDirectAuthUri = userDirectAuthUri;
    }

    public String getAppUserSearchUri() {
        return appUserSearchUri;
    }

    public void setAppUserSearchUri(String appUserSearchUri) {
        this.appUserSearchUri = appUserSearchUri;
    }
}
