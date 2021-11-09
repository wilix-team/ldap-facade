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

package dev.wilix.ldap.facade.espo;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * User store built on top of Wilix CRM.
 *
 * TODO Migrate to ignoreCaseMap
 */
public class EspoDataStorage implements DataStorage {
    // TODO Need to add user verification to the flag isActive
    private final static Logger LOG = LoggerFactory.getLogger(EspoDataStorage.class);
    private static final String USER_AVATAR_PROPERTY_NAME = "jpegPhoto";

    private boolean loadUsersAvatars;

    private final RequestHelper requestHelper;
    private final AvatarHelper avatarHelper;
    private final EntityParser entityParser;
    private final Cache<Authentication, List<Map<String, List<String>>>> users;
    private final Cache<Authentication, List<Map<String, List<String>>>> groups;

    private final String authenticateUserUri;
    private final String searchAllUsersUri;
    private final String searchAllGroupsUri;

    public EspoDataStorage(RequestHelper requestHelper, EntityParser entityParser, int cacheExpirationMinutes, String baseUrl, boolean loadUsersAvatars) {
        this.requestHelper = requestHelper;
        this.avatarHelper = new AvatarHelper(baseUrl, requestHelper);
        this.entityParser = entityParser;

        this.loadUsersAvatars = loadUsersAvatars;

        users = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheExpirationMinutes, TimeUnit.MINUTES)
                .build();

        groups = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheExpirationMinutes, TimeUnit.MINUTES)
                .build();

        try {
            authenticateUserUri = new URIBuilder(baseUrl).setPath("/api/v1/App/user").build().toString();
            searchAllUsersUri = new URIBuilder(baseUrl).setPath("/api/v1/User")
                    .addParameter("select", "emailAddress,teamsIds").build().toString();
            searchAllGroupsUri = new URIBuilder(baseUrl).setPath("/api/v1/Team").build().toString();

        } catch (URISyntaxException e) {
            LOG.debug("Problem with URIBuilder:", e);
            throw new IllegalStateException("Problem with URIBuilder", e);
        }
    }

    // TODO Solve code duplications

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            // If it returns without error, everything is fine.
            Map<String, List<String>> userInfo = checkAuthentication(new UserAuthentication(userName, password));

            if (userInfo.isEmpty()) {
                throw new IllegalStateException("Unexpected result from user authentication");
            }

            return new UserAuthentication(userName, password, true);
        } catch (Exception ex) {
            LOG.debug("Wrong user credentials: {}:{}", userName, password);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        try {
            // If it returns without error, everything is fine.
            Map<String, List<String>> userInfo = checkAuthentication(new ServiceAuthentication(serviceName, token));

            if (userInfo.isEmpty()) {
                throw new IllegalStateException("Unexpected result from service authentication");
            }

            return new ServiceAuthentication(serviceName, token, true);
        } catch (Exception ex) {
            LOG.debug("Wrong service credentials: {}:{}", serviceName, token);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        try {
            return users.get(authentication, () -> performUsersSearch(authentication));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        try {
            return groups.get(authentication, () -> performGroupsSearch(authentication));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private Map<String, List<String>> checkAuthentication(Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequestForJson(authenticateUserUri, authentication);
        return entityParser.parseUserInfo(response.get("user"));
    }

    private List<Map<String, List<String>>> performGroupsSearch(Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequestForJson(searchAllGroupsUri, authentication);
        List<Map<String, List<String>>> groups = StreamSupport.stream(response.get("list").spliterator(), false)
                .map(entityParser::parseGroupInfo)
                .collect(Collectors.toList());

        // FIXME Think about what to do with such an explicit declaration of attribute names.
        // Get group members based on the list of users and enrich the groups with this information.
        var users = getAllUsers(authentication);
        Map<String, List<String>> groupToUsers = new HashMap<>();
        for (Map<String, List<String>> user : users) {
            String userName = user.get("uid").get(0);
            user.get("memberof").forEach(
                    groupName -> groupToUsers.computeIfAbsent(groupName, s -> new ArrayList<>()).add(userName));
        }

        groups.forEach(group -> group.put("member", groupToUsers.getOrDefault(group.get("uid").get(0), Collections.emptyList())));

        return groups;
    }

    private List<Map<String, List<String>>> performUsersSearch(Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequestForJson(searchAllUsersUri, authentication);

        List<Map<String, List<String>>> users = StreamSupport.stream(response.get("list").spliterator(), false)
                .map(entityParser::parseUserInfo)
                .collect(Collectors.toList());

        if (loadUsersAvatars) {
            users.forEach(user -> user.put(USER_AVATAR_PROPERTY_NAME, List.of(avatarHelper.getAvatarByUserId(user.get("id").get(0), authentication))));
        }

        return users;
    }
}