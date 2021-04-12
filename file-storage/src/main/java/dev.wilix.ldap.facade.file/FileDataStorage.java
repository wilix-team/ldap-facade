package dev.wilix.ldap.facade.file;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileDataStorage implements DataStorage, Authentication {
    private final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FileDataStorage.class);

    private String pathToFile;

    private boolean isSuccess;

    public FileDataStorage(FileStorageConfigurationProperties config) {
        this.pathToFile = config.getPathToFile();
    }

    public void watchFileChanges() {
        WatchService watchService;
        boolean poll = true;
        WatchKey watchKey;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path pathToFileWatching = Paths.get(pathToFile);
            Path directoryToFileWatching = pathToFileWatching.getParent();
            directoryToFileWatching.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            LOGGER.error("Problem with watching file:  " + e);
            throw new IllegalStateException("Problem with watching file:  " + e);
        }

        while (poll) {
            try {
                watchKey = watchService.take();
            } catch (InterruptedException e) {
                throw new IllegalStateException("The operation was interrupted: " + e);
            }
            if (watchKey != null) {
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    LOGGER.info("Event kind: " + event.kind() + " - for file: " + event.context());
                }
                poll = watchKey.reset();
            }
        }
    }

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readValue(Paths.get(pathToFile).toFile(), JsonNode.class);
            JsonNode users = jsonNode.get("users");
            System.out.println(users);
            for (JsonNode user : users) {
                if ((user.get("userName").asText().equals(userName) && user.get("password").asText().equals(password))) {
                    return () -> true;
                }
            }
            return Authentication.NEGATIVE;

        } catch (JsonParseException e) {
            LOGGER.error("Errors received while running the parser: " + e);
            return Authentication.NEGATIVE;
        } catch (JsonMappingException e) {
            LOGGER.error("Errors received while mapping json: " + e);
            return Authentication.NEGATIVE;
        } catch (IOException e) {
            LOGGER.error("Errors received while trying to read the file: " + e);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return Authentication.NEGATIVE;
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        if (authentication.isSuccess()) {
            return performUsersSearch();
        }
        return null;
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        if (authentication.isSuccess()) {
            return performGroupsSearch();
        }
        return null;
    }

    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    private List<Map<String, List<String>>> performUsersSearch() {
        try {
            List<Map<String, List<String>>> listOfUsers = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readValue(Paths.get(pathToFile).toFile(), JsonNode.class);
            JsonNode users = jsonNode.get("users");
            for (JsonNode user : users) {
                listOfUsers.add(parseUserInfo(user));
            }
            return listOfUsers;

        } catch (JsonParseException e) {
            LOGGER.error("Errors received while running the parser: " + e);
            throw new RuntimeException("The input is not valid JSON: " + e);
        } catch (JsonMappingException e) {
            LOGGER.error("Errors received while mapping json: " + e);
            throw new RuntimeException("Error mapping JSON: " + e);
        } catch (IOException e) {
            LOGGER.error("Errors received while trying to read the file: " + e);
            throw new IllegalStateException("Errors received while trying to read the file: " + e);
        }
    }

    private Map<String, List<String>> parseUserInfo(JsonNode user) {
        Map<String, List<String>> usersInfo = new HashMap<>();

        if (user != null) {
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = user.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };
            usersInfo.put("company", List.of("WILIX"));

            jsonToUserFieldSetter.accept("id", value -> usersInfo.put("id", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> usersInfo.put("entryuuid", List.of(value)));
            jsonToUserFieldSetter.accept("userName", value -> usersInfo.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> usersInfo.put("cn", List.of(value)));
            jsonToUserFieldSetter.accept("phoneNumber", value -> usersInfo.put("telephoneNumber", List.of(value)));
            jsonToUserFieldSetter.accept("emailAddress", value -> usersInfo.put("mail", List.of(value)));

            List<String> memberOfList = new ArrayList<>();
            JsonNode teamsNamesNode = user.get("teamsNames");
            if (teamsNamesNode != null) {
                teamsNamesNode.elements()
                        .forEachRemaining((teamNameNode) -> memberOfList.add(teamNameNode.textValue()));
            }
            usersInfo.put("memberof", memberOfList);

            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            usersInfo.put("vcsName", vcsName);
        }
        return usersInfo;
    }

    private List<Map<String, List<String>>> performGroupsSearch() {
        try {
            List<Map<String, List<String>>> listOfGroups = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readValue(Paths.get(pathToFile).toFile(), JsonNode.class);
            JsonNode groups = jsonNode.get("groups");
            for (JsonNode group : groups) {
                listOfGroups.add(parseGroupInfo(group));
            }
            return listOfGroups;

        } catch (JsonParseException e) {
            LOGGER.error("Errors received while running the parser: " + e);
            throw new RuntimeException("The input is not valid JSON: " + e);
        } catch (JsonMappingException e) {
            LOGGER.error("Errors received while mapping json: " + e);
            throw new RuntimeException("Error mapping JSON: " + e);
        } catch (IOException e) {
            LOGGER.error("Errors received while trying to read the file: " + e);
            throw new IllegalStateException("Errors received while trying to read the file: " + e);
        }
    }

    private Map<String, List<String>> parseGroupInfo(JsonNode group) {
        Map<String, List<String>> info = new HashMap<>();

        if (group != null) {
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = group.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };
            jsonToUserFieldSetter.accept("id", value -> info.put("id", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("primarygrouptoken", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("gidnumber", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("entryuuid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));

            List<String> memberOfList = new ArrayList<>();
            JsonNode membersNode = group.get("member");
            if (membersNode != null) {
                membersNode.elements()
                        .forEachRemaining((memberNode) -> memberOfList.add(memberNode.textValue()));
            }
            info.put("member", memberOfList);
        }
        return info;
    }
}
