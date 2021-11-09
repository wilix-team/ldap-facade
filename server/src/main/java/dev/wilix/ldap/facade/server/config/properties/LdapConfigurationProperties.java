/*
 * Copyright 2021 WILIX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.wilix.ldap.facade.server.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO Enable validation.
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
    private int searchCacheExpirationMinutes = 10;

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

    public int getSearchCacheExpirationMinutes() {
        return searchCacheExpirationMinutes;
    }

    public void setSearchCacheExpirationMinutes(int searchCacheExpirationMinutes) {
        this.searchCacheExpirationMinutes = searchCacheExpirationMinutes;
    }
}
