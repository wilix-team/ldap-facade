package dev.wilix.ldap.facade.file.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.file.FileDataStorage;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({FileStorageConfigurationProperties.class})
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "file")
public class FileStorageConfig {

    @Bean
    public DataStorage userDataStorage(FileStorageConfigurationProperties config) {
        return new FileDataStorage(config, objectMapper());
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
