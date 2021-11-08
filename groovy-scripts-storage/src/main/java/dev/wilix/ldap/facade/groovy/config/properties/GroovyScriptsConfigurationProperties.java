package dev.wilix.ldap.facade.groovy.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotEmpty;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "storage.groovy")
public class GroovyScriptsConfigurationProperties {

    @NotEmpty
    private Path scriptPath;

    public Path getScriptPath() {
        return scriptPath;
    }

    public void setScriptPath(Path scriptPath) {
        this.scriptPath = scriptPath;
    }
}
