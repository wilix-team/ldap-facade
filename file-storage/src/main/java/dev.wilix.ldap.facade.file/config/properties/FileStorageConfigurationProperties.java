package dev.wilix.ldap.facade.file.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "storage.file")
public class FileStorageConfigurationProperties {

    @NotNull
    private Path pathToFile;

    @NotNull
    @PositiveOrZero
    private int fileWatchInterval;

    public Path getPathToFile() {
        return this.pathToFile;
    }

    public void setPathToFile(Path pathToFile) {
        this.pathToFile = pathToFile;
    }

    public int getFileWatchInterval() {
        return fileWatchInterval;
    }

    public void setFileWatchInterval(int fileWatchInterval) {
        this.fileWatchInterval = fileWatchInterval;
    }
}
