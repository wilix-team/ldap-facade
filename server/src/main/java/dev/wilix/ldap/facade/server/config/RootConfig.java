/*
 * Copyright 2021 WILIX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     * Preparing the ldap connection listener configuration to be able to accept connections over a secure channel.
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
        return new SearchOperationProcessor(dataStorage, ldapNamingHelper(), ldapConfig.getSearchCacheExpirationMinutes());
    }

    @Bean
    public LDAPListenerRequestHandler requestHandler(BindOperationProcessor bindOperationProcessor,
                                                     SearchOperationProcessor searchOperationProcessor) {
        return new UserBindAndSearchRequestHandler(bindOperationProcessor, searchOperationProcessor);
    }

}