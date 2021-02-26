package dev.wilix.ldap.facade.wilix.crm.facade.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO Включить валидацию для этой штуки.
 */
@ConfigurationProperties(prefix = "listener")
public class ServerConfigurationProperties {

    /**
     * A listen port of zero indicates that the listener should
     * automatically pick a free port on the system.
     */
    private int port = 10389;

    private boolean sslEnabled = true;

    private String keyStorePath;

    private String keyStorePass;

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

}