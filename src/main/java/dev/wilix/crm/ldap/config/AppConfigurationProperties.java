package dev.wilix.crm.ldap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO Включить валидацию для этой штуки.
 */
@ConfigurationProperties(prefix = "listener")
public class AppConfigurationProperties {

    /**
     * A listen port of zero indicates that the listener should
     * automatically pick a free port on the system.
     */
    private int port = 10389;

    private boolean sslEnabled = true;

    private String keyStorePath;

    private String keyStorePass;

    private String staffURI;

    private String crmURI;

    private int expireTime;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePass() {
        return keyStorePass;
    }

    public void setKeyStorePass(String keyStorePass) {
        this.keyStorePass = keyStorePass;
    }

    public String getStaffURI() {
        return staffURI;
    }

    public void setStaffURI(String staffURI) {
        this.staffURI = staffURI;
    }

    public String getCrmURI() {
        return crmURI;
    }

    public void setCrmURI(String crmURI) {
        this.crmURI = crmURI;
    }

    public int getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(int expireTime) {
        this.expireTime = expireTime;
    }

}