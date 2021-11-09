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

package dev.wilix.ldap.facade.api;

import java.util.List;
import java.util.Map;

/**
 * Integration interface for receiving data from an external source.
 */
public interface DataStorage {

    /**
     * Direct user authentication.
     *
     * @param userName User name.
     * @param password User password.
     * @return Result of authentication.
     */
    Authentication authenticateUser(String userName, String password);

    /**
     * Service account authentication.
     *
     * @param serviceName Service name.
     * @param token       API token.
     * @return Result of authentication.
     */
    Authentication authenticateService(String serviceName, String token);

    /**
     * Getting a list of users with groups.
     *
     * @param authentication Information about the current user \ service authentication.
     * @return Result of search.
     */
    List<Map<String, List<String>>> getAllUsers(Authentication authentication);

    /**
     * Getting a list of groups with users
     *
     * @param authentication Information about the current user \ service authentication.
     * @return Result of search
     */
    List<Map<String, List<String>>> getAllGroups(Authentication authentication);
}