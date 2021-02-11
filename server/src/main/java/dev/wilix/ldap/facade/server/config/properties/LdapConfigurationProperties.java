package dev.wilix.ldap.facade.server.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Класс с базовыми
 *
 * TODO Включить валидацию для этой штуки.
 */
@ConfigurationProperties(prefix = "ldap")
public class LdapConfigurationProperties {
    private String baseDn;
    private String servicesBaseDn;
    private String usersBaseDn;
    private String groupsBaseDn;
    private String userClassName;
    private String groupClassName;
    private String mainNameAttribute;

    public String getBaseDn() {
        return baseDn;
    }

    public void setBaseDn(String baseDn) {
        this.baseDn = baseDn;
    }

    public String getServicesBaseDn() {
        return servicesBaseDn;
    }

    public void setServicesBaseDn(String servicesBaseDn) {
        this.servicesBaseDn = servicesBaseDn;
    }

    public String getUsersBaseDn() {
        return usersBaseDn;
    }

    public void setUsersBaseDn(String usersBaseDn) {
        this.usersBaseDn = usersBaseDn;
    }

    public String getGroupsBaseDn() {
        return groupsBaseDn;
    }

    public void setGroupsBaseDn(String groupsBaseDn) {
        this.groupsBaseDn = groupsBaseDn;
    }

    public String getUserClassName() {
        return userClassName;
    }

    public void setUserClassName(String userClassName) {
        this.userClassName = userClassName;
    }

    public String getGroupClassName() {
        return groupClassName;
    }

    public void setGroupClassName(String groupClassName) {
        this.groupClassName = groupClassName;
    }

    public String getMainNameAttribute() {
        return mainNameAttribute;
    }

    public void setMainNameAttribute(String mainNameAttribute) {
        this.mainNameAttribute = mainNameAttribute;
    }
}
