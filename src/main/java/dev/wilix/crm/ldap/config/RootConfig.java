package dev.wilix.crm.ldap.config;

import com.unboundid.ldap.listener.LDAPListener;
import com.unboundid.ldap.listener.LDAPListenerConfig;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.util.ssl.KeyStoreKeyManager;
import com.unboundid.util.ssl.SSLUtil;
import dev.wilix.crm.ldap.model.CrmUserDataStorage;
import dev.wilix.crm.ldap.model.UserBindAndSearchRequestHandler;
import dev.wilix.crm.ldap.model.UserDataStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.security.GeneralSecurityException;

@Configuration
@EnableConfigurationProperties(AppConfigurationProperties.class)
public class RootConfig {

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
            String serverKeyStorePath = Path.of(config.getKeyStorePath()).toFile().getAbsolutePath();
            KeyStoreKeyManager keyManager = new KeyStoreKeyManager(serverKeyStorePath, config.getKeyStorePass().toCharArray());
            final SSLUtil serverSSLUtil = new SSLUtil(keyManager, null);

            ldapListenerConfig.setServerSocketFactory(serverSSLUtil.createSSLServerSocketFactory("TLSv1.2"));
        }


        return ldapListenerConfig;
    }

    @Bean
    public LDAPListenerRequestHandler requestHandler(UserDataStorage userDataStorage) {
        return new UserBindAndSearchRequestHandler(userDataStorage);
    }

    @Bean
    public UserDataStorage userDataStorage() {
        return new CrmUserDataStorage();
    }
}
