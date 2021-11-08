package dev.wilix.ldap.facade.groovy.config;

import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.groovy.GroovyClassLoadException;
import dev.wilix.ldap.facade.groovy.config.properties.GroovyScriptsConfigurationProperties;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.net.URI;

@Configuration
@EnableConfigurationProperties({GroovyScriptsConfigurationProperties.class})
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "groovy")
public class GroovyScriptsStorageConfig {

    @Autowired
    GroovyScriptsConfigurationProperties configurationProperties;

    @Bean
    public DataStorage groovyDataStorage() {
        return groovyDataStorage(configurationProperties.getScriptPath().toUri());
    }

    protected DataStorage groovyDataStorage(URI scriptPath) {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());

        try {
            Class<?> loadedClass = loader.parseClass(new File(scriptPath));
            return (DataStorage) loadedClass.getDeclaredConstructor().newInstance();
        }
        catch (CompilationFailedException e) {
            throw new GroovyClassLoadException("Error while compiling script.", e);
        }
        catch (ClassCastException e) {
            throw new GroovyClassLoadException("Class cast exception after creating groovy class instance.", e);
        }
        catch (ReflectiveOperationException e) {
            throw new GroovyClassLoadException("Reflective exception while creating groovy class instance.", e);
        }
        catch (IOException e) {
            throw new GroovyClassLoadException("IOException while reading groovy script file.", e);
        }
    }
}
