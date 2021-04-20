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

import java.util.List;
import java.util.Map;

/**
 * Выдача результатов парсинга файла.
 * <p>
 * Класс предназначен для хранения и выдачи из результатов парсинга файла, полученных в классе FileParser,
 * информации пользователей и групп.
 */

class ParseResult {
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
        return "ParseResult{" +
                "users=" + users +
                ", groups=" + groups +
                '}';
    }
}
