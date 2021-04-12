package dev.wilix.ldap.facade.file.config;

import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.file.FileDataStorage;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({FileStorageConfigurationProperties.class})
public class FileStorageConfig {

    @Bean
    public DataStorage userDataStorage(FileStorageConfigurationProperties config) {
        return new FileDataStorage(config);
    }

}
