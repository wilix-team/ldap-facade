package dev.wilix.ldap.facade.groovy.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.validation.constraints.NotEmpty;
import java.nio.file.Path;

@ConfigurationProperties(prefix = "storage.groovy")
public class GroovyScriptsConfigurationProperties {

    @NotEmpty
    private Path scriptPath;

    public Path getPathToScript() {
        return scriptPath;
    }

    public void setPathToScript(Path pathToScript) {
        this.scriptPath = pathToScript;
    }
}
