package dev.wilix.ldap.facade.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.ldap.listener.LDAPListener;
import com.unboundid.ldap.listener.LDAPListenerConfig;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import dev.wilix.ldap.facade.espo.CrmUserDataStorage;
import dev.wilix.ldap.facade.server.Application;
import dev.wilix.ldap.facade.api.UserDataStorage;
import dev.wilix.ldap.facade.server.config.properties.AppConfigurationProperties;
import dev.wilix.ldap.facade.espo.properties.UserDataStorageConfigurationProperties;
import dev.wilix.ldap.facade.server.model.UserBindAndSearchRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({AppConfigurationProperties.class, UserDataStorageConfigurationProperties.class})
public class RootConfig {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Autowired
    AppConfigurationProperties config;

    @Bean
    public LDAPListener ldapListener(LDAPListenerConfig listenerConfig) {
        return new LDAPListener(listenerConfig);
    }

    @Bean
    public LDAPListenerConfig listenerConfig(LDAPListenerRequestHandler requestHandler) throws GeneralSecurityException {

        LDAPListenerConfig ldapListenerConfig = new LDAPListenerConfig(config.getPort(), requestHandler);

        if (config.isSslEnabled()) {
            LOG.info("SSL is turned on. Configuring...");

            configureSSL(ldapListenerConfig);
        } else {
            LOG.info("SSL is turned off...");
        }

        return ldapListenerConfig;
    }

    /**
     * Подготовка конфигурации слушателя ldap-соединения для возможности принимать соединения по защищенному каналу.
     */
    private void configureSSL(LDAPListenerConfig ldapListenerConfig) throws GeneralSecurityException {
        var serverKeyStorePath = Path.of(config.getKeyStorePath()).toFile().getAbsolutePath();
        var keyManager = new KeyStoreKeyManager(serverKeyStorePath, config.getKeyStorePass().toCharArray());
        var serverSSLUtil = new SSLUtil(keyManager, null);

        ldapListenerConfig.setServerSocketFactory(serverSSLUtil.createSSLServerSocketFactory("TLSv1.2"));
    }

    @Bean
    public LDAPListenerRequestHandler requestHandler(UserDataStorage userDataStorage) {
        return new UserBindAndSearchRequestHandler(userDataStorage);
    }

    @Bean
    public UserDataStorage userDataStorage(UserDataStorageConfigurationProperties config) {
        return new CrmUserDataStorage(httpClient(), objectMapper(), config);
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)) // TODO Возможно потребуется выносить в настройки.
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}