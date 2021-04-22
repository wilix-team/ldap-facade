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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EntityParser {

    private final Map<String, List<String>> additionalUserAttributes;

    public EntityParser(Map<String, List<String>> additionalUserAttributes) {
        this.additionalUserAttributes = additionalUserAttributes;
    }

    /**
     * Парсинг пользователя из формата ответа от CRM.
     *
     * @param userJsonField Json поле с информацией о пользователе.
     * @return Разобранная информация о пользователе в ожидаемом формате.
     */
    Map<String, List<String>> parseUserInfo(JsonNode userJsonField) {
        Map<String, List<String>> info = new HashMap<>();

        if (userJsonField != null) {
            // Приемник для корректной установки свойств пользователя из ответа сервера в свойства пользователя для ldap.
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = userJsonField.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            // TODO Нужно формировать display name.
            jsonToUserFieldSetter.accept("id", value -> info.put("id", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("entryuuid", List.of(value)));
            jsonToUserFieldSetter.accept("userName", value -> info.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));
            jsonToUserFieldSetter.accept("phoneNumber", value -> info.put("telephoneNumber", List.of(value)));
            jsonToUserFieldSetter.accept("emailAddress", value -> info.put("mail", List.of(value)));
            jsonToUserFieldSetter.accept("firstName", value -> info.put("gn", List.of(value)));
            jsonToUserFieldSetter.accept("lastName", value -> info.put("sn", List.of(value)));
            jsonToUserFieldSetter.accept("isActive", value -> info.put("active", List.of(value)));

            List<String> memberOfList = new ArrayList<>();
            JsonNode teamsNamesNode = userJsonField.get("teamsNames");
            if (teamsNamesNode != null) {
                teamsNamesNode.elements()
                        .forEachRemaining((teamNameNode) -> memberOfList.add(teamNameNode.textValue()));
            }
            info.put("memberof", memberOfList);

            // атрибут для имени в vcs системах (git)
            List<String> vcsName = new ArrayList<>(2);
            jsonToUserFieldSetter.accept("name", vcsName::add);
            jsonToUserFieldSetter.accept("emailAddress", vcsName::add);
            info.put("vcsName", vcsName);

            // FIXME Решить вопрос перезатирания записей, когда приходящие атрибуты совпадают со значениями от сервера
            //  добавляются дополнительные свойства пользователя в общее хранилище свойств пользователя
            for (Map.Entry<String, List<String>> tagInformation : additionalUserAttributes.entrySet()) {
                if ( ! (info.containsKey(tagInformation.getKey()) && info.get(tagInformation.getKey()).equals(tagInformation.getValue())))
                {
                    info.put(tagInformation.getKey(), tagInformation.getValue());
                }
            }
        }

        return info;
    }

    /**
     * TODO Документация.
     *
     * @param groupJsonNode
     * @return
     */
    Map<String, List<String>> parseGroupInfo(JsonNode groupJsonNode) {
        Map<String, List<String>> info = new HashMap<>();

        if (groupJsonNode != null) {
            // Приемник для корректной установки свойств пользователя из ответа сервера в свойства пользователя для ldap.
            BiConsumer<String, Consumer<String>> jsonToUserFieldSetter = (fieldName, fieldSetter) -> {
                JsonNode fieldNode = groupJsonNode.get(fieldName);
                if (fieldNode != null) {
                    fieldSetter.accept(fieldNode.asText());
                }
            };

            jsonToUserFieldSetter.accept("id", value -> info.put("id", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("primarygrouptoken", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("gidnumber", List.of(value)));
            jsonToUserFieldSetter.accept("id", value -> info.put("entryuuid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("uid", List.of(value)));
            jsonToUserFieldSetter.accept("name", value -> info.put("cn", List.of(value)));
        }

        return info;
    }
}
