package dev.wilix.ldap.facade.file.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.file.FileDataStorage;
import dev.wilix.ldap.facade.file.FileParser;
import dev.wilix.ldap.facade.file.FileWatcher;
import dev.wilix.ldap.facade.file.ParseResult;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;

@Configuration
@EnableConfigurationProperties({FileStorageConfigurationProperties.class})
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "file")
public class FileStorageConfig {

    @Autowired
    private FileStorageConfigurationProperties config;

    @Bean
    public DataStorage userDataStorage(FileParser fileParser) throws IOException {

        final ParseResult initialState = fileParser.parseFileContent(Files.readString(config.getPathToFile()));

        return new FileDataStorage(initialState, fileParser);
    }

    @Bean
    public FileParser fileParser(ObjectMapper objectMapper) {
        return new FileParser(objectMapper);
    }

    @Bean
    public FileWatcher fileWatcher(FileDataStorage fileDataStorage) {
        FileWatcher fileWatcher = new FileWatcher(config, fileDataStorage::performParse);
        fileWatcher.watchFileChanges();

        return fileWatcher;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
