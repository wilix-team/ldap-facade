package dev.wilix.ldap.facade.groovy;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

public class GroovyScriptsDataStorage implements DataStorage {

    private final GroovyObject targetClass;

    public GroovyScriptsDataStorage(URI scriptUri) {
        GroovyClassLoader loader = new GroovyClassLoader(getClass().getClassLoader());

        try {
            Class<?> loadedClass = loader.parseClass(new File(scriptUri));

            // Проверка на то, что класс реализует интерфейс DataStorage

            if (loadedClass.isAssignableFrom(DataStorage.class)) {
                this.targetClass = (GroovyObject) loadedClass.getDeclaredConstructor().newInstance();
            }

            else {
                throw new ReflectiveOperationException("Target groovy class " + loadedClass.getName() +
                        " is not implements DataStorage interface.");
            }
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
        return null;
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return null;
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        return null;
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        return null;
    }
}
