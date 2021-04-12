package dev.wilix.ldap.facade.file;

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
    FileStorageConfigurationProperties configurationProperties;

    @Test
    public void authenticationUserCheck() {
        FileDataStorage fileDataStorage = new FileDataStorage(configurationProperties);
        Authentication authenticateUser = fileDataStorage.authenticateUser("username", "password");
        assertEquals(true, authenticateUser.isSuccess());
    }

    @Test
    private void authentificationServiceCheck() {
        FileDataStorage fileDataStorage = new FileDataStorage(configurationProperties);
        Authentication authenticateService = fileDataStorage.authenticateService("serviceName", "token");
        assertEquals(false, authenticateService.isSuccess());
    }

    @Test
    public void receiveUsersInfo() {
        FileDataStorage fileDataStorage = new FileDataStorage(configurationProperties);
        Authentication authentication = fileDataStorage.authenticateUser("username", "password");
        List<Map<String, List<String>>> usersInfo = fileDataStorage.getAllUsers(authentication);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "telephoneNumber", "mail", "memberof", "vcsName"};
        usersInfo.forEach(e -> checkAttributes(e, attributes));
    }

    @Test
    public void receiveGroupsInfo() {
        FileDataStorage fileDataStorage = new FileDataStorage(configurationProperties);
        Authentication authentication = fileDataStorage.authenticateUser("username", "password");
        List<Map<String, List<String>>> groupsInfo = fileDataStorage.getAllGroups(authentication);
        String[] attributes = {"id", "entryuuid", "uid", "cn", "gidnumber", "primarygrouptoken", "member"};
        groupsInfo.forEach(e -> checkAttributes(e, attributes));
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
