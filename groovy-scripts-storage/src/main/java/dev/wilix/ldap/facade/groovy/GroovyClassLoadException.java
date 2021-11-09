package dev.wilix.ldap.facade.groovy;

/**
 * Runtime wrapper for exceptions, thrown during Groovy class loading.
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
