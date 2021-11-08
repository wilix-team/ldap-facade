package dev.wilix.ldap.facade.groovy.config;

import dev.wilix.ldap.facade.groovy.GroovyClassLoadException;
import dev.wilix.ldap.facade.groovy.TestGroovyApplication;
import org.codehaus.groovy.control.CompilationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestGroovyApplication.class)
public class GroovyScriptsDataStorageCreationTest {
    private final String WRONG_FILE_NAME = "GroovyWrongClass.groovy";

    private final String WRONG_SYNTAX_NAME = "GroovyWrongSyntax.groovy";

    @Autowired
    private GroovyScriptsStorageConfig config;

    @Test
    public void wrongClass() throws URISyntaxException {
        URI uri = Objects.requireNonNull(getClass().getClassLoader().getResource(WRONG_FILE_NAME)).toURI();

        assertThrows(GroovyClassLoadException.class, () -> config.groovyDataStorage(uri));

        // Провека того, что именно ClassCastException стала причиной

        try {
            config.groovyDataStorage(uri);
        }

        catch (GroovyClassLoadException groovyException) {
            assertTrue(groovyException.getCause() instanceof ClassCastException);
        }
    }

    @Test
    public void syntaxError() throws URISyntaxException {
        URI uri = Objects.requireNonNull(getClass().getClassLoader().getResource(WRONG_SYNTAX_NAME)).toURI();

        assertThrows(GroovyClassLoadException.class, () -> config.groovyDataStorage(uri));

        // Провека того, что именно наследник или сам CompilationFailedException стали причиной

        try {
            config.groovyDataStorage(uri);
        }

        catch (GroovyClassLoadException groovyException) {
            if (groovyException.getClass().isAssignableFrom(CompilationFailedException.class)) {
                fail();
            }
        }
    }

    @Test
    public void fileNotFound() throws URISyntaxException {
        try {
            config.groovyDataStorage(new URI("file:///never/exists/path/PlsDontCreateThisFile.groovy"));
            fail(); // если сюда дошел - нет эксепшена => fail
        }

        catch (GroovyClassLoadException groovyException) {
            if (!(groovyException.getCause() instanceof FileNotFoundException)) {
                fail();
            }
        }
    }
}
