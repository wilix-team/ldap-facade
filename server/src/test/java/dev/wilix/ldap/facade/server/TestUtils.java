package dev.wilix.ldap.facade.server;

class TestUtils {
    static final String USERS_AND_GROUPS_JSON_PATH = "users_and_groups_file.json";

    static final String USERNAME = "username";
    static final String USER_PASSWORD = "password";
    static final String NAME_OF_SERVICE = "serviceName";
    static final String TOKEN_OF_SERVICE = "token";

    static final String BASE_DN = "dc=example,dc=com";
    static final String USER_BASE_DN = "ou=people,dc=example,dc=com";
    static final String GROUP_BASE_DN = "ou=groups,dc=example,dc=com";
    static final String SERVICE_BASE_DN = "ou=services,dc=example,dc=com";

    static final String[] ALL_USERS_ATTRIBUTES = {"company", "id", "entryuuid", "uid", "cn", "gn", "sn", "active", "telephoneNumber", "mail", "vcsName", "memberof"};
    static final String[] ALL_GROUPS_ATTRIBUTES = {"id", "primarygrouptoken", "gidnumber", "entryuuid", "uid", "cn", "member"};

}
