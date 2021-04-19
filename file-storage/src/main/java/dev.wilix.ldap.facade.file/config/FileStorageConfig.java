package dev.wilix.ldap.facade.file.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.file.FileDataStorage;
import dev.wilix.ldap.facade.file.FileParser;
import dev.wilix.ldap.facade.file.FileWatcher;
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

        String fileContent = Files.readString(config.getPathToFile());
        FileDataStorage fileDataStorage = new FileDataStorage(fileParser);
        fileDataStorage.performParse(fileContent);
        return fileDataStorage;
    }

    @Bean
    public FileParser fileParser(ObjectMapper objectMapper) {
        return new FileParser(objectMapper);
    }

    @Bean
    public FileWatcher fileWatcher(FileDataStorage fileDataStorage) {
        FileWatcher fileWatcher = new FileWatcher(config.getPathToFile(), config.getFileWatchInterval(), fileDataStorage::performParse);
        fileWatcher.watchFileChanges();

        return fileWatcher;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
