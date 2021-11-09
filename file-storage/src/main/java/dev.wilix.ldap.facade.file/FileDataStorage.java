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

package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementing a file-based user and group storage.
 */
public class FileDataStorage implements DataStorage {

    private final FileParser fileParser;

    private final ReadWriteLock updateDataLock = new ReentrantReadWriteLock();
    private List<Map<String, List<String>>> users = Collections.emptyList();
    private List<Map<String, List<String>>> groups = Collections.emptyList();
    private Map<String, String> usersPasswordInfo;

    public FileDataStorage(FileParser fileParser) {
        this.fileParser = fileParser;
    }

    public void performParse(String fileContent) {
        final ParseResult parseResult = fileParser.parseFileContent(fileContent);

        Lock lock = updateDataLock.writeLock();
        lock.lock();
        try {
            users = parseResult.getUsers();
            groups = parseResult.getGroups();

            // Puts data about password in separate storage and deletes it from main storage.
            Map<String, String> newUsersPasswordInfo = new HashMap<>();
            for (Map<String, List<String>> user : users) {
                // TODO Make it grabs fields safely
                newUsersPasswordInfo.put(user.get("uid").get(0), user.get("password").get(0));
                user.remove("password");
            }
            usersPasswordInfo = newUsersPasswordInfo;

        } finally {
            lock.unlock();
        }
    }

    @Override
    public FileUserAuthentication authenticateUser(String userName, String password) {
        FileUserAuthentication fileUserAuthentication = new FileUserAuthentication();
        fileUserAuthentication.setUserName(userName);

        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            if (usersPasswordInfo.containsKey(userName) && usersPasswordInfo.get(userName).equals(password)) {
                fileUserAuthentication.setSuccess(true);
                return fileUserAuthentication;
            }
        } finally {
            lock.unlock();
        }

        return fileUserAuthentication;
    }

    /**
     * Service account authentication.
     *
     * Service authentication is not used in this repository due to the fact that there is no request to the server.
     * Authentication by checking for the presence of the appropriate username and password in a local json file
     * containing exclusively user and group data.
     */
    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return Authentication.NEGATIVE;
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        if ( ! authentication.isSuccess()) {
            throw new IllegalStateException("Access denied");
        }

        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            return users;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        if ( ! authentication.isSuccess()) {
            throw new IllegalStateException("Access denied");
        }

        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            return groups;
        } finally {
            lock.unlock();
        }
    }
}
