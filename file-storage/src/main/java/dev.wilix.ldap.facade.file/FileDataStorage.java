package dev.wilix.ldap.facade.file;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileDataStorage implements DataStorage {
    private final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FileDataStorage.class);

    private final String pathToFile;
    private final ObjectMapper objectMapper;
    private List<Map<String, List<String>>> users;
    private List<Map<String, List<String>>> groups;

    public FileDataStorage(FileStorageConfigurationProperties config, ObjectMapper objectMapper) {
        this.pathToFile = config.getPathToFile();
        this.objectMapper = objectMapper;
    }

    public void watchFileChanges() {
        WatchService watchService;
        WatchKey watchKey;

        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path pathToFileWatching = Paths.get(pathToFile);
            Path directoryToFileWatching = pathToFileWatching.getParent();
            directoryToFileWatching.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            LOGGER.error("Problem with watching file:  ", e);
            throw new IllegalStateException("Problem with watching file:  ", e);
        }

        boolean poll = true;
        while (poll) {
            try {
                watchKey = watchService.take();
            } catch (InterruptedException e) {
                LOGGER.error("The operation was interrupted: ", e);
                throw new IllegalStateException("The operation was interrupted: ", e);
            }
            if (watchKey != null) {
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    LOGGER.info("Event kind: " + event.kind() + " - for file: " + event.context());
                }
                parseFile();
                poll = watchKey.reset();
            }
        }
    }

    @PostConstruct
    public void postConstruct() {
        parseFile();
    }


    @Override
    public Authentication authenticateUser(String userName, String password) {
        for (Map<String, List<String>> user : users) {
            if (user.get("uid").get(0).equals(userName) && user.get("password").get(0).equals(password)) {
                return Authentication.POSITIVE;
            }
        }

        return Authentication.NEGATIVE;
    }

    /**
     * Аутентификация сервисного аккаунта.
     *
     * В данном хранилище не используется сервисная аутентификация по причине того, что
     * никакой запрос на сервер не отправляется, а пользовательская аутентификация проходит
     * путём проверки наличия соответствующих логина и пароля пользователя в локальном файле формата json,
     * содержащем исключительно данные пользователей и групп.
     */

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return Authentication.NEGATIVE;
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        if (authentication.isSuccess()) {
            return users;
        }
        throw new IllegalStateException("Access denied");
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        if (authentication.isSuccess()) {
            return groups;
        }
        throw new IllegalStateException("Access denied");
    }

    private void parseFile() {
        try {
            users = new ArrayList<>();
            groups = new ArrayList<>();
            JsonNode jsonNode = objectMapper.readValue(Paths.get(pathToFile).toFile(), JsonNode.class);
            performGroupsSearch(jsonNode.get("groups"));
            performUsersSearch(jsonNode.get("users"));

        } catch (JsonParseException e) {
            LOGGER.error("Errors received while running the parser: ", e);
            throw new IllegalStateException("The input is not valid JSON: ", e);
        } catch (JsonMappingException e) {
            LOGGER.error("Errors received while mapping json: ", e);
            throw new IllegalStateException("Error mapping JSON: ", e);
        } catch (IOException e) {
            LOGGER.error("Errors received while trying to read the file: ", e);
            throw new IllegalStateException("Errors received while trying to read the file: ", e);
        }
    }

    private void performUsersSearch(JsonNode usersNode) {
        for (JsonNode userNode : usersNode) {
            users.add(parseUserInfo(userNode));
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
            jsonToUserFieldSetter.accept("password", value -> usersInfo.put("password", List.of(value)));

            List<String> memberOfList = new ArrayList<>();

            for (Map<String, List<String>> group : groups) {
                if (group.get("member").contains(user.get("userName").asText())) {
                    memberOfList.add(group.get("uid").get(0));
                }
            }

            usersInfo.put("memberof", memberOfList);

            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            usersInfo.put("vcsName", vcsName);
        }
        return usersInfo;
    }

    private void performGroupsSearch(JsonNode groupsNode) {
        for (JsonNode groupNode : groupsNode) {
            groups.add(parseGroupInfo(groupNode));
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
