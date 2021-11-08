package dev.wilix.ldap.facade.groovy;

/**
 * Runtime обертка для эксепшенов, возникающих во время чтения и создания объекта класса из Groovy.
 */
public class GroovyClassLoadException extends RuntimeException {
    public GroovyClassLoadException() {
    }

    public GroovyClassLoadException(String message) {
        super(message);
    }

    public GroovyClassLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public GroovyClassLoadException(Throwable cause) {
        super(cause);
    }
}
