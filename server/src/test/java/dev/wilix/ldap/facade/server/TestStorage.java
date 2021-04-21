package dev.wilix.ldap.facade.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.Authentication;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class TestStorage implements dev.wilix.ldap.facade.api.DataStorage {

    private List<Map<String, List<String>>> users = null;
    private List<Map<String, List<String>>> groups = null;

    private final Path PATH_TO_FILE = Path.of("./src/test/resources/users_and_groups_file.json");

    private final String USERNAME = "username";
    private final String USER_PASSWORD = "password";
    private final String NAME_OF_SERVICE = "serviceName";
    private final String TOKEN_OF_SERVICE = "token";

    @PostConstruct
    private void prepareEntriesInfo() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, List<Map<String, List<String>>>> usersAndGroupsInfo = objectMapper.readValue(PATH_TO_FILE.toFile(), Map.class);
        users = usersAndGroupsInfo.get("users");
        groups = usersAndGroupsInfo.get("groups");
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        return authentication.isSuccess() ? users : null;
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        return authentication.isSuccess() ? groups : null;
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
