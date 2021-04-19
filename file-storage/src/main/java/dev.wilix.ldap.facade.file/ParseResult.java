package dev.wilix.ldap.facade.file;

import java.util.List;
import java.util.Map;

/**
 * Выдача результатов парсинга файла.
 * <p>
 * Класс предназначен для хранения и выдачи из результатов парсинга файла, полученных в классе FileParser,
 * информации пользователей и групп.
 */

public class ParseResult {
    private final List<Map<String, List<String>>> users;
    private final List<Map<String, List<String>>> groups;

    public ParseResult(List<Map<String, List<String>>> users, List<Map<String, List<String>>> groups) {
        this.users = users;
        this.groups = groups;
    }

    public List<Map<String, List<String>>> getUsers() {
        return users;
    }

    public List<Map<String, List<String>>> getGroups() {
        return groups;
    }

    @Override
    public String toString() {
        StringBuilder parseInfo = new StringBuilder();

        parseInfo.append("ParseResult{\n" + "users={\n");

        for (Map<String, List<String>> user : users) {
            parseInfo.append(user.get("uid").get(0)).append("={").append(user).append("},\n");
        }

        parseInfo.append("},\n groups={\n");

        for (Map<String, List<String>> group : groups) {
            parseInfo.append(group.get("uid").get(0)).append("={").append(group).append("},\n");
        }

        parseInfo.append("}\n" + '}');

        return parseInfo.toString();
    }
}
