package dev.wilix.crm.ldap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.ldap.listener.LDAPListener;
import com.unboundid.ldap.listener.LDAPListenerConfig;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import dev.wilix.crm.ldap.Application;
import dev.wilix.crm.ldap.model.CrmUserDataStorage;
import dev.wilix.crm.ldap.model.UserBindAndSearchRequestHandler;
import dev.wilix.crm.ldap.model.UserDataStorage;
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
@EnableConfigurationProperties(AppConfigurationProperties.class)
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

        // TODO Нужно сделать это настройкой.
        if (config.isSslEnabled()) {
            LOG.info("SSL is turned on. Configuring...");

            String serverKeyStorePath = Path.of(config.getKeyStorePath()).toFile().getAbsolutePath();
            KeyStoreKeyManager keyManager = new KeyStoreKeyManager(serverKeyStorePath, config.getKeyStorePass().toCharArray());
            final SSLUtil serverSSLUtil = new SSLUtil(keyManager, null);

            ldapListenerConfig.setServerSocketFactory(serverSSLUtil.createSSLServerSocketFactory("TLSv1.2"));
        } else {
            LOG.info("SSL is turned off...");
        }


        return ldapListenerConfig;
    }

    @Bean
    public LDAPListenerRequestHandler requestHandler(UserDataStorage userDataStorage) {
        return new UserBindAndSearchRequestHandler(userDataStorage);
    }

    @Bean
    public UserDataStorage userDataStorage() {
        return new CrmUserDataStorage(httpClient(), objectMapper());
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}