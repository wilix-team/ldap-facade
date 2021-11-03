package dev.wilix.ldap.facade.groovy;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class GroovyScriptsDataStorage implements DataStorage {

    /**
     *  Обернутый объект DataStorage из Groovy
     */
    private final DataStorage groovyDataStorage;

    Logger logger = LoggerFactory.getLogger(GroovyClassLoadException.class);

    public GroovyScriptsDataStorage(URI scriptUri) {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());

        try {
            Class<?> loadedClass = loader.parseClass(new File(scriptUri));
            this.groovyDataStorage = (DataStorage) loadedClass.getDeclaredConstructor().newInstance();
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

    @Override
    public Authentication authenticateUser(String userName, String password) {
        return groovyDataStorage.authenticateUser(userName, password);
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return groovyDataStorage.authenticateService(serviceName, token);
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        return groovyDataStorage.getAllUsers(authentication);
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        return groovyDataStorage.getAllGroups(authentication);
    }
}
