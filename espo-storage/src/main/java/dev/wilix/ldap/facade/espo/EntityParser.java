package dev.wilix.ldap.facade.espo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EntityParser {

    /**
     * Парсинг пользователя из формата ответа от CRM.
     *
     * @param userJsonField Json поле с информацией о пользователе.
     * @return Разобранная информация о пользователе в ожидаемом формате.
     */
    static Map<String, List<String>> parseUserInfo(JsonNode userJsonField) {
        Map<String, List<String>> info = new HashMap<>();

        if (userJsonField != null) {
            // Приемник для корректной установки свойств пользователя из ответа сервера в свойства пользователя для ldap.
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = userJsonField.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            info.put("company", List.of("WILIX"));

            // TODO Нужно формировать display name.
            jsonToUserFieldSetter.accept("id", value -> info.put("id", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("entryuuid", List.of(value)));
            jsonToUserFieldSetter.accept("userName", value -> info.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));
            jsonToUserFieldSetter.accept("phoneNumber", value -> info.put("telephoneNumber", List.of(value)));
            jsonToUserFieldSetter.accept("emailAddress", value -> info.put("mail", List.of(value)));
            jsonToUserFieldSetter.accept("firstName", value -> info.put("gn", List.of(value)));
            jsonToUserFieldSetter.accept("lastName", value -> info.put("sn", List.of(value)));
            jsonToUserFieldSetter.accept("isActive", value -> info.put("active", List.of(value)));

            List<String> memberOfList = new ArrayList<>();
            JsonNode teamsNamesNode = userJsonField.get("teamsNames");
            if (teamsNamesNode != null) {
                teamsNamesNode.elements()
                        .forEachRemaining((teamNameNode) -> memberOfList.add(teamNameNode.textValue()));
            }
            info.put("memberof", memberOfList);

            // атрибут для имени в vcs системах (git)
            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            info.put("vcsName", vcsName);
        }

        return info;
    }

    /**
     * TODO Документация.
     *
     * @param groupJsonNode
     * @return
     */
    static Map<String, List<String>> parseGroupInfo(JsonNode groupJsonNode) {
        Map<String, List<String>> info = new HashMap<>();

        if (groupJsonNode != null) {
            // Приемник для корректной установки свойств пользователя из ответа сервера в свойства пользователя для ldap.
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = groupJsonNode.get(fieldName);
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
        }

        return info;
    }
}
