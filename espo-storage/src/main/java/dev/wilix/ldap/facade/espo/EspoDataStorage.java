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
import dev.wilix.ldap.facade.espo.config.properties.EspoDataStorageConfigurationProperties;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Хранилище пользователей, построенное на основе Wilix CRM.
 *
 * TODO Требуется перейти на ignoreCaseMap
 */
public class EspoDataStorage implements DataStorage {
    // TODO Нужно добавить проверку у пользователей на флаг isActive
    private final static Logger LOG = LoggerFactory.getLogger(EspoDataStorage.class);

    private final RequestHelper requestHelper;
    private final EntityParser entityParser;
    private final Cache<Authentication, List<Map<String, List<String>>>> users;
    private final Cache<Authentication, List<Map<String, List<String>>>> groups;

    private final String authenticateUserUri;
    private final String searchAllUsersUri;
    private final String searchAllGroupsUri;

    public EspoDataStorage(RequestHelper requestHelper, EntityParser entityParser, int cacheExpirationMinutes, String baseUrl) {
        this.requestHelper = requestHelper;
        this.entityParser = entityParser;

        users = CacheBuilder.newBuilder()
                .expireAfterAccess(cacheExpirationMinutes, TimeUnit.MINUTES)
                .build();

        groups = CacheBuilder.newBuilder()
                .expireAfterAccess(cacheExpirationMinutes, TimeUnit.MINUTES)
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

    @Override
    public Authentication authenticateUser(String userName, String password) {
        try {
            UserAuthentication auth = new UserAuthentication();
            auth.setUserName(userName);
            auth.setPassword(password);

            // Если вернется без ошибки - все хорошо.
            Map<String, List<String>> userInfo = checkAuthentication(auth);

            if (userInfo.isEmpty()) {
                throw new IllegalStateException("Unexpected result from user authentication");
            }

            auth.setSuccess(true);
            return auth;
        } catch (Exception ex) {
            LOG.debug("Wrong user credentials: {}:{}", userName, password);
            return Authentication.NEGATIVE;
        }
    }

    @Override
    public Authentication authenticateService(String serviceName, String token) {
        try {
            ServiceAuthentication auth = new ServiceAuthentication();
            auth.setServiceName(serviceName);
            auth.setToken(token);

            // Если вернется без ошибки - все хорошо.
            Map<String, List<String>> userInfo = checkAuthentication(auth);

            if (userInfo.isEmpty()) {
                throw new IllegalStateException("Unexpected result from service authentication");
            }

            auth.setSuccess(true);
            return auth;
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
        JsonNode response = requestHelper.sendCrmRequest(authenticateUserUri, authentication);
        return entityParser.parseUserInfo(response.get("user"));
    }

    private List<Map<String, List<String>>> performGroupsSearch(Authentication authentication) {
        JsonNode response = requestHelper.sendCrmRequest(searchAllGroupsUri, authentication);
        List<Map<String, List<String>>> groups = StreamSupport.stream(response.get("list").spliterator(), false)
                .map(entityParser::parseGroupInfo)
                .collect(Collectors.toList());

        // FIXME Подумать, что делать с таким явным объявление названий атрибутов.
        // Получаем участников групп на основе списка пользователей и обогащаем этой информацией группы.
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
        JsonNode response = requestHelper.sendCrmRequest(searchAllUsersUri, authentication);

        return StreamSupport.stream(response.get("list").spliterator(), false)
                .map(entityParser::parseUserInfo)
                .collect(Collectors.toList());
    }

}