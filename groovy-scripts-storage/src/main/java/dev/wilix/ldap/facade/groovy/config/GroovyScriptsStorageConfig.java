package dev.wilix.ldap.facade.groovy.config;

import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.groovy.GroovyScriptsDataStorage;
import dev.wilix.ldap.facade.groovy.config.properties.GroovyScriptsConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@EnableConfigurationProperties({GroovyScriptsConfigurationProperties.class})
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "groovy")
public class GroovyScriptsStorageConfig {

    @Autowired
    GroovyScriptsConfigurationProperties configurationProperties;

    @Bean
    public DataStorage groovyDataStorage() {
        URI scriptUri = configurationProperties.getScriptPath().toUri();
        return new GroovyScriptsDataStorage(scriptUri);
    }
}
