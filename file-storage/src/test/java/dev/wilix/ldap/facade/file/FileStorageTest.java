package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    private final String FILE_WITH_CORRECT_USERS_AND_GROUPS_INFO = "./src/test/resources/correct_users_and_groups_file.json";
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
        assertEquals(0, usersInfo.stream().filter(u -> u.containsKey("password")).count());

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
    public void fileParserEmptyContent() throws IOException {
        assertThrows(NullPointerException.class, () -> fileParser.parseFileContent(""));
    }

    @Test
    public void correctParseFile() throws IOException {
        String fileContent = Files.readString(fileStorageConfigurationProperties.getPathToFile());
        fileParser.parseFileContent(fileContent);
    }

    @Test
    public void checkUpdateFileEventWhileThisFileWatching() throws IOException, InterruptedException {
        Path testFilePath = Path.of("./src/test/resources/check_watcher_file.json");

        try {
            Files.createFile(testFilePath);
            Files.writeString(testFilePath, "{\"users\":[],\"groups\":[]}");

            FileDataStorage fileDataStorage = new FileDataStorage(fileParser);
            fileDataStorage.performParse(Files.readString(testFilePath));
            FileWatcher fileWatcher = new FileWatcher(testFilePath, 15, fileDataStorage::performParse);
            fileWatcher.watchFileChanges();

            Thread.sleep(150);

            assertEquals(0, fileDataStorage.getAllUsers(Authentication.POSITIVE).size());
            assertEquals(0, fileDataStorage.getAllGroups(Authentication.POSITIVE).size());

            String fileContent = Files.readString(Path.of(FILE_WITH_CORRECT_USERS_AND_GROUPS_INFO));
            Files.writeString(testFilePath, fileContent);

            Thread.sleep(150);

            assertTrue(fileDataStorage.getAllUsers(Authentication.POSITIVE).size() != 0);
            assertTrue(fileDataStorage.getAllGroups(Authentication.POSITIVE).size() != 0);
        } finally {
            Files.delete(testFilePath);
        }
    }

    @Test
    public void exceptionFileNotExistWhileTryingThisFileWatching() {
        FileStorageConfigurationProperties config = new FileStorageConfigurationProperties();
        config.setPathToFile(Path.of(WRONG_PATH_TO_FILE));

        assertThrows(IllegalStateException.class, () -> new FileWatcher(config.getPathToFile(), config.getFileWatchInterval(), fileDataStorage::performParse));
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
