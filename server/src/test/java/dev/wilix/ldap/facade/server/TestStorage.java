package dev.wilix.ldap.facade.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.Authentication;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.util.List;
import java.util.Map;

import static dev.wilix.ldap.facade.server.TestUtils.*;

public class TestStorage implements dev.wilix.ldap.facade.api.DataStorage {

    private List<Map<String, List<String>>> users = null;
    private List<Map<String, List<String>>> groups = null;

    public TestStorage() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File usersAndGroupsInfoFile = new ClassPathResource(USERS_AND_GROUPS_JSON_PATH).getFile();

            Map<String, List<Map<String, List<String>>>> usersAndGroupsInfo = objectMapper.readValue(usersAndGroupsInfoFile, Map.class);
            users = usersAndGroupsInfo.get("users");
            groups = usersAndGroupsInfo.get("groups");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        if (!authentication.isSuccess()) {
            throw new IllegalStateException("Access denied");
        }
        return users;
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        if (!authentication.isSuccess()) {
            throw new IllegalStateException("Access denied");
        }
        return groups;
    }

    @Override
    public Authentication authenticateUser(String username, String password) {
        return (username.equals(USERNAME) && password.equals(USER_PASSWORD)) ? Authentication.POSITIVE : Authentication.NEGATIVE;
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return (serviceName.equals(NAME_OF_SERVICE) && token.equals(TOKEN_OF_SERVICE)) ? Authentication.POSITIVE : Authentication.NEGATIVE;
    }
}
