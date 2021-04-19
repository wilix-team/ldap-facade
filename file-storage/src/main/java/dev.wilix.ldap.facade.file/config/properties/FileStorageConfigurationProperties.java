package dev.wilix.ldap.facade.file.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "storage.file")
public class FileStorageConfigurationProperties {

    @NotNull
    private Path pathToFile;

    @NotNull
    @Positive
    private Integer fileWatchInterval = 10_000;

    public Path getPathToFile() {
        return this.pathToFile;
    }

    public void setPathToFile(Path pathToFile) {
        this.pathToFile = pathToFile;
    }

    public Integer getFileWatchInterval() {
        return fileWatchInterval;
    }

    public void setFileWatchInterval(Integer fileWatchInterval) {
        this.fileWatchInterval = fileWatchInterval;
    }
}
