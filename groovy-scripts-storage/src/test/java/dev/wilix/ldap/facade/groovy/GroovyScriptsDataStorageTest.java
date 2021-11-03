package dev.wilix.ldap.facade.groovy;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.groovy.config.properties.GroovyScriptsConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тестирование работы класса.
 */
@SpringBootTest(classes = TestGroovyApplication.class)
class GroovyScriptsDataStorageTest {

    @Autowired
    private GroovyScriptsConfigurationProperties prop;

    private DataStorage dataStorage;

    @BeforeEach
    void setUp() throws URISyntaxException {
        dataStorage = new GroovyScriptsDataStorage(prop.getScriptPath().toUri());
    }

    @Test
    void authenticateUser() {
        assertEquals(dataStorage.authenticateUser("username", "password"), Authentication.POSITIVE);
        assertEquals(dataStorage.authenticateUser("username", "wrong"), Authentication.NEGATIVE);
        assertEquals(dataStorage.authenticateUser("wrong", "password"), Authentication.NEGATIVE);
        assertEquals(dataStorage.authenticateUser("wrong", "wrong"), Authentication.NEGATIVE);
        assertEquals(dataStorage.authenticateUser("", ""), Authentication.NEGATIVE);
    }

    @Test
    void authenticateService() {
        // Тестовый класс не использует сервисы
        assertEquals(dataStorage.authenticateService("", ""), Authentication.NEGATIVE);
    }

    @Test
    void getAllUsers() {
        List<Map<String, List<String>>> expected = List.of(
            Map.of(
                "id", List.of("1"),
                "userName", List.of("username"),
                "password", List.of("password"),
                "phoneNumber", List.of("554611"),
                "emailAddress", List.of("addres@ad.ru"),
                "name", List.of("vcsName")
            ),

            Map.of(
                "id", List.of("2"),
                "userName", List.of("username2"),
                "password", List.of("password2"),
                "phoneNumber", List.of("22222"),
                "emailAddress", List.of("addres2@ad.ru"),
                "name", List.of("vcsName2")
            )
        );

        List<Map<String, List<String>>> actual = dataStorage.getAllUsers(Authentication.POSITIVE);

        assertEquals(expected, actual);

        List<Map<String, List<String>>> partialData = List.of(
            Map.of(
                    "id", List.of("1"),
                    "userName", List.of("username"),
                    "password", List.of("password"),
                    "phoneNumber", List.of("554611"),
                    "emailAddress", List.of("addres@ad.ru"),
                    "name", List.of("vcsName")
            )
        );

        assertNotEquals(expected, partialData);
    }

    @Test
    void getAllGroups() {
        List<Map<String, List<String>>> expected = List.of(
            Map.of(
                "id", List.of("1"),
                "name", List.of("test"),
                "member", List.of("username", "username2")
            ),

            Map.of(
                "id", List.of("2"),
                "name", List.of("test2"),
                "member", List.of("username", "username2")
            ),

            Map.of(
                "id", List.of("3"),
                "name", List.of("employee"),
                "member", List.of("username")
            )
        );

        List<Map<String, List<String>>> actual = dataStorage.getAllGroups(Authentication.POSITIVE);

        assertEquals(expected, actual);
    }
}