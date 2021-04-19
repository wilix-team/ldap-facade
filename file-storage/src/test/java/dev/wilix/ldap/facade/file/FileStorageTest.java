package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
public class FileStorageTest {

    @Autowired
    FileParser fileParser;
    @Autowired
    FileDataStorage fileDataStorage;
    @Autowired
    FileStorageConfigurationProperties fileStorageConfigurationProperties;
    private final String FILE_FOR_CHANGING_CHECK = "./src/test/resources/changing_file_for_check_file_watcher.json";
    private final String FILE_WITH_CORRECT_USERS_AND_GROUPS_INFO = "./src/test/resources/correct_users_and_groups_file.json";
    private final String FILE_EMPTY = "./src/test/resources/empty_json_for_wrong_mapping.json";
    private final String FILE_WITH_WRONG_JSON_FORMAT = "./src/test/resources/wrong_json_format.json";

    private final String WRONG_PATH_TO_FILE = "a/b/c";

    private final String USERNAME = "username";
    private final String PASSWORD = "password";

    @Test
    public void positiveAuthenticationUserCheck() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileDataStorage.performParse(fileContent);

        FileUserAuthentication authenticateUser = fileDataStorage.authenticateUser(USERNAME, PASSWORD);
        assertTrue(authenticateUser.isSuccess());
    }

    @Test
    public void attemptAuthenticateThenReceiveUsersInfoThenAttemptAuthenticate() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileDataStorage.performParse(fileContent);

        FileUserAuthentication authenticateUser = fileDataStorage.authenticateUser(USERNAME, PASSWORD);
        assertTrue(authenticateUser.isSuccess());

        List<Map<String, List<String>>> usersInfo = fileDataStorage.getAllUsers(authenticateUser);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));

        authenticateUser = fileDataStorage.authenticateUser(USERNAME, PASSWORD);
        assertTrue(authenticateUser.isSuccess());
    }

    @Test
    public void negativeAuthenticationUserCheck() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileDataStorage.performParse(fileContent);

        FileUserAuthentication authenticateUser = fileDataStorage.authenticateUser("wrongUser", "wrongPassword");
        assertFalse(authenticateUser.isSuccess());
    }

    @Test
    public void negativeAuthenticationServiceCheck() {
        Authentication authenticateService = fileDataStorage.authenticateService("serviceName", "token");
        assertFalse(authenticateService.isSuccess());
    }

    @Test
    public void receiveUsersInfo() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileDataStorage.performParse(fileContent);

        FileUserAuthentication authentication = fileDataStorage.authenticateUser(USERNAME, PASSWORD);
        List<Map<String, List<String>>> usersInfo = fileDataStorage.getAllUsers(authentication);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
    }

    @Test
    public void exceptionOfReceiveUsersInfo() {
        assertThrows(IllegalStateException.class, () -> fileDataStorage.getAllUsers(Authentication.NEGATIVE));
    }

    @Test
    public void receiveGroupsInfo() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileDataStorage.performParse(fileContent);

        FileUserAuthentication authentication = fileDataStorage.authenticateUser(USERNAME, PASSWORD);
        List<Map<String, List<String>>> groupsInfo = fileDataStorage.getAllGroups(authentication);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "gidnumber", "primarygrouptoken", "member"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
    }

    @Test
    public void exceptionOfReceiveGroupsInfo() {
        assertThrows(IllegalStateException.class, () -> fileDataStorage.getAllGroups(Authentication.NEGATIVE));
    }

    @Test
    public void exceptionReadingNotExistentFile() {
        assertThrows(IllegalStateException.class, () -> fileParser.parseFileContent(WRONG_PATH_TO_FILE));
    }

    @Test
    public void exceptionIllegalArgumentOfPath() {
        assertThrows(IllegalArgumentException.class, () -> fileParser.parseFileContent(null));
    }

    @Test
    public void exceptionWrongJsonFormat() throws IOException {
        String fileContent = Files.readString(Path.of(FILE_WITH_WRONG_JSON_FORMAT));
        assertThrows(IllegalStateException.class, () -> fileParser.parseFileContent(fileContent));
    }

    @Test
    public void exceptionNullPointerOfFileContent() throws IOException {
        String fileContent = Files.readString(Path.of(FILE_EMPTY));
        assertThrows(NullPointerException.class, () -> fileParser.parseFileContent(fileContent));
    }

    @Test
    public void correctParseFile() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileParser.parseFileContent(fileContent);
    }

    @Test
    public void checkUpdateFileEventWhileThisFileWatching() throws IOException {
        String fileContent = Files.readString(Path.of(FILE_WITH_CORRECT_USERS_AND_GROUPS_INFO));

        FileStorageConfigurationProperties config = new FileStorageConfigurationProperties();
        config.setPathToFile(Path.of(FILE_FOR_CHANGING_CHECK));

        FileWatcher fileWatcher = new FileWatcher(config, fileDataStorage::performParse);
        fileWatcher.watchFileChanges();

        FileWriter writer = new FileWriter(FILE_FOR_CHANGING_CHECK);
        writer.write(fileContent);
        writer.flush();
    }

    @Test
    public void exceptionFileNotExistWhileTryingThisFileWatching() {
        FileStorageConfigurationProperties config = new FileStorageConfigurationProperties();
        config.setPathToFile(Path.of(WRONG_PATH_TO_FILE));

        FileWatcher fileWatcher = new FileWatcher(config, fileDataStorage::performParse);
        assertThrows(IllegalStateException.class, fileWatcher::watchFileChanges);
    }

    private void checkAttributes(Map<String, List<String>> e, String... attributes) {
        for (String attributeName : attributes) {
            if (attributeName.equals("memberof") || attributeName.equals("member")) {
                assertFalse(e.get(attributeName).isEmpty());
            } else {
                assertNotNull(e.get(attributeName));
            }
        }
    }
}
