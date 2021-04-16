package dev.wilix.ldap.facade.file.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "storage.file")
public class FileStorageConfigurationProperties {

    @NotNull
    private Path pathToFile;

    public Path getPathToFile() {
        return this.pathToFile;
    }

    public void setPathToFile(Path pathToFile) {
        this.pathToFile = pathToFile;
    }
}
