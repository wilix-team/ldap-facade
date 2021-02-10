package dev.wilix.ldap.facade.server.config;

import com.unboundid.ldap.listener.LDAPListener;
import com.unboundid.ldap.listener.LDAPListenerConfig;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.server.config.properties.LdapConfigurationProperties;
import dev.wilix.ldap.facade.server.config.properties.ServerConfigurationProperties;
import dev.wilix.ldap.facade.server.processing.BindOperationProcessor;
import dev.wilix.ldap.facade.server.processing.LdapNamingHelper;
import dev.wilix.ldap.facade.server.processing.SearchOperationProcessor;
import dev.wilix.ldap.facade.server.processing.UserBindAndSearchRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.GeneralSecurityException;

@Configuration
@EnableConfigurationProperties({ServerConfigurationProperties.class, LdapConfigurationProperties.class})
public class RootConfig {

    private static final Logger LOG = LoggerFactory.getLogger(RootConfig.class);

    @Autowired
    ServerConfigurationProperties serverConfig;

    @Autowired
    LdapConfigurationProperties ldapConfig;

    @Bean
    public LDAPListener ldapListener(LDAPListenerConfig listenerConfig) {
        return new LDAPListener(listenerConfig);
    }

    @Bean
    public LDAPListenerConfig listenerConfig(LDAPListenerRequestHandler requestHandler) throws GeneralSecurityException {

        LDAPListenerConfig ldapListenerConfig = new LDAPListenerConfig(serverConfig.getPort(), requestHandler);

        if (serverConfig.isSslEnabled()) {
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
        var serverKeyStorePath = Path.of(serverConfig.getKeyStorePath()).toFile().getAbsolutePath();
        var keyManager = new KeyStoreKeyManager(serverKeyStorePath, serverConfig.getKeyStorePass().toCharArray());
        var serverSSLUtil = new SSLUtil(keyManager, null);

        ldapListenerConfig.setServerSocketFactory(serverSSLUtil.createSSLServerSocketFactory("TLSv1.2"));
    }

    @Bean
    public LdapNamingHelper ldapNamingHelper() {
        return new LdapNamingHelper(ldapConfig);
    }

    @Bean
    public BindOperationProcessor bindOperationProcessor(DataStorage dataStorage) {
        return new BindOperationProcessor(dataStorage, ldapNamingHelper());
    }

    @Bean
    public SearchOperationProcessor searchOperationProcessor(DataStorage dataStorage) {
        return new SearchOperationProcessor(dataStorage, ldapNamingHelper());
    }

    @Bean
    public LDAPListenerRequestHandler requestHandler(BindOperationProcessor bindOperationProcessor,
                                                     SearchOperationProcessor searchOperationProcessor) {
        return new UserBindAndSearchRequestHandler(bindOperationProcessor, searchOperationProcessor);
    }

}