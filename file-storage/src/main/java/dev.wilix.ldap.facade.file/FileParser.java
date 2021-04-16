package dev.wilix.ldap.facade.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileParser {

    private final static Logger LOGGER = LoggerFactory.getLogger(FileParser.class);

    private final ObjectMapper objectMapper;

    public FileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParseResult parseFileContent(String fileContent) {
        try {
            JsonNode jsonNode = objectMapper.readTree(fileContent);

            List<Map<String, List<String>>> groups = performGroupsParsing(jsonNode.get("groups"));
            List<Map<String, List<String>>> users = performUsersParsing(groups, jsonNode.get("users"));

            return new ParseResult(users, groups);

        } catch (JsonProcessingException e) {
            LOGGER.error("Errors received while running the parser: ", e);
            throw new IllegalStateException("Errors received while running the parser: ", e);
        }
    }

    private List<Map<String, List<String>>> performUsersParsing(List<Map<String, List<String>>> groups, JsonNode usersNode) {
        List<Map<String, List<String>>> users = new ArrayList<>();
        for (JsonNode userNode : usersNode) {
            users.add(parseUserInfo(groups, userNode));
        }
        return users;
    }

    private Map<String, List<String>> parseUserInfo(List<Map<String, List<String>>> groups, JsonNode user) {
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

            // TODO пароль передавать нельзя, переработать
            jsonToUserFieldSetter.accept("password", value -> usersInfo.put("password", List.of(value)));

            List<String> memberOfList = new ArrayList<>();

            for (Map<String, List<String>> group : groups) {
                LOGGER.info("groups " + groups);
                LOGGER.info("group " + group);
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

    private List<Map<String, List<String>>> performGroupsParsing(JsonNode groupsNode) {
        List<Map<String, List<String>>> groups = new ArrayList<>();
        for (JsonNode groupNode : groupsNode) {
            groups.add(parseGroupInfo(groupNode));
        }
        return groups;
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
