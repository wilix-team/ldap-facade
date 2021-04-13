package dev.wilix.ldap.facade.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
public class UsersReceiveTest {

    @Autowired
    FileDataStorage fileDataStorage;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    public void exceptionReadingNotExistentFile() {
        assertThrows(IllegalStateException.class, createFileDataStorage("a/b/c/d")::postConstruct);
    }

    @Test
    public void exceptionNullOfPath() {
        assertThrows(NullPointerException.class, createFileDataStorage(null)::postConstruct);
    }

    @Test
    public void exceptionWrongJsonFormat() {
        assertThrows(IllegalStateException.class, createFileDataStorage("./src/test/resources/wrong_json_format.json")::postConstruct);
    }

    @Test
    public void exceptionWrongJsonMappingIfFileIsEmpty() {
        assertThrows(IllegalStateException.class, createFileDataStorage("./src/test/resources/empty_json_for_wrong_mapping.json")::postConstruct);
    }

    @Test
    public void correctParseFile() {
        assertDoesNotThrow(fileDataStorage::postConstruct);
    }

    @Test
    public void authenticationUserCheck() {
        Authentication authenticateUser = fileDataStorage.authenticateUser("username", "password");
        assertTrue(authenticateUser.isSuccess());
    }

    @Test
    public void authentificationUserWrongCheck() {
        Authentication authenticateUser = fileDataStorage.authenticateUser("wrongUser", "wrongPassword");
        assertFalse(authenticateUser.isSuccess());
    }

    @Test
    public void authentificationServiceCheck() {
        Authentication authenticateService = fileDataStorage.authenticateService("serviceName", "token");
        assertFalse(authenticateService.isSuccess());
    }

    @Test
    public void receiveUsersInfo() {
        Authentication authentication = fileDataStorage.authenticateUser("username", "password");
        List<Map<String, List<String>>> usersInfo = fileDataStorage.getAllUsers(authentication);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "memberof", "vcsName", "password"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
    }

    @Test
    public void exceptionOfReceiveUsersInfo() {
        Authentication authentication = Authentication.NEGATIVE;
        assertThrows(IllegalStateException.class, () -> fileDataStorage.getAllUsers(authentication));
    }

    @Test
    public void receiveGroupsInfo() {
        Authentication authentication = fileDataStorage.authenticateUser("username", "password");
        List<Map<String, List<String>>> groupsInfo = fileDataStorage.getAllGroups(authentication);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "gidnumber", "primarygrouptoken", "member"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
    }

    @Test
    public void exceptionOfReceiveGroupsInfo() {
        Authentication authentication = Authentication.NEGATIVE;
        assertThrows(IllegalStateException.class, () -> fileDataStorage.getAllGroups(authentication));
    }

    @Test
    public void exceptionWrongPathWhileTryingWatchingFile() {
        assertThrows(IllegalStateException.class, createFileDataStorage("a/d/c/f/g")::watchFileChanges);
    }

    private FileDataStorage createFileDataStorage(String pathToFile) {
        FileStorageConfigurationProperties fileStorageConfigurationProperties = new FileStorageConfigurationProperties();
        fileStorageConfigurationProperties.setPathToFile(pathToFile);
        return new FileDataStorage(fileStorageConfigurationProperties, objectMapper);
    }

    private void checkAttributes(Map<String, List<String>> e, String... attributes) {
        for (String attributeName : attributes) {
            if (attributeName.equals("memberof") || attributeName.equals("member")) {
                assertFalse(e.get(attributeName).isEmpty());
            } else {
                Assertions.assertNotNull(e.get(attributeName));
            }
        }
    }
}
