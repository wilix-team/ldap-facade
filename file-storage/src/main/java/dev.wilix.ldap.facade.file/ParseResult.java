package dev.wilix.ldap.facade.file;

import java.util.List;
import java.util.Map;

public class ParseResult { // TODO Доработать!
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
}
