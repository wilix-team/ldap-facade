package dev.wilix.ldap.facade.server;

import dev.wilix.ldap.facade.api.Authentication;
import org.springframework.boot.SpringApplication;

import javax.annotation.PostConstruct;
import java.util.*;

public class TestStorage implements dev.wilix.ldap.facade.api.DataStorage {
    private List<Map<String, List<String>>> users = null;
    private List<Map<String, List<String>>> groups = null;
    private final String userName = "username";
    private final String userPassword = "password";
    private final String nameOfService = "serviceName";
    private final String tokenOfService = "token";
    private final String[] ALL_USERS_ATTRIBUTES = {"company", "id", "entryuuid", "uid", "cn", "gn", "sn", "active", "telephoneNumber", "mail", "memberof", "vcsName"};
    private final String[] ALL_GROUPS_ATTRIBUTES = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn", "member"};
    private final List<List<String>> FIRST_USER_VALUES = Arrays.asList(List.of("Wilix"), List.of("1"), List.of("1"), List.of("username"), List.of("FirstName SecondName"), List.of("FirstName"), List.of("SecondName"), List.of("true"), List.of("88005553535"), List.of("email@e.mail"), List.of("groupOne", "groupTwo"), List.of("FirstName SecondName", "88005553535"));
    private final List<List<String>> SECOND_USER_VALUES = Arrays.asList(List.of("Wilix"), List.of("2"), List.of("2"), List.of("username2"), List.of("FirstName2 SecondName2"), List.of("FirstName2"), List.of("SecondName2"), List.of("false"), List.of("12345678900"), List.of("email2@e.mail"), List.of("groupOne"), List.of("FirstName2 SecondName2", "12345678900"));
    private final List<List<String>> FIRST_GROUP_VALUES = Arrays.asList(List.of("1"), List.of("1"), List.of("1"), List.of("1"), List.of("groupOne"), List.of("groupOne"), List.of("username", "username2"));
    private final List<List<String>> SECOND_GROUP_VALUES = Arrays.asList(List.of("1"), List.of("2"), List.of("2"), List.of("2"), List.of("groupTwo"), List.of("groupTwo"), List.of("username"));

    public static void main(String[] args) {
        SpringApplication.run(TestStorage.class, args);
    }

    @PostConstruct
    private void prepareEntriesInfo() {
        users = addUsers();
        groups = addGroups();
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
        return (username.equals(userName) && password.equals(userPassword)) ? Authentication.POSITIVE : Authentication.NEGATIVE;
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return (serviceName.equals(nameOfService) && token.equals(tokenOfService)) ? Authentication.POSITIVE : Authentication.NEGATIVE;
    }

    private List<Map<String, List<String>>> addUsers() {
        return List.of(addInfoToMap(ALL_USERS_ATTRIBUTES, FIRST_USER_VALUES), addInfoToMap(ALL_USERS_ATTRIBUTES, SECOND_USER_VALUES));
    }

    private List<Map<String, List<String>>> addGroups() {
        return List.of(addInfoToMap(ALL_GROUPS_ATTRIBUTES, FIRST_GROUP_VALUES), addInfoToMap(ALL_GROUPS_ATTRIBUTES, SECOND_GROUP_VALUES));
    }

    private Map<String, List<String>> addInfoToMap(String[] attributesNames, List<List<String>> attributesValues) {
        Map<String, List<String>> infoOfEntry = new HashMap<>();
        for (int i = 0; i < attributesNames.length; i++) {
            infoOfEntry.put(attributesNames[i], attributesValues.get(i));
        }
        return infoOfEntry;
    }

}
