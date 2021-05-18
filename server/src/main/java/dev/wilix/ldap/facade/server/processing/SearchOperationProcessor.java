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

package dev.wilix.ldap.facade.server.processing;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.unboundid.ldap.listener.SearchEntryParer;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * TODO Вынести названия атрибутов member и memberOf в настройки ldap.
 */
public class SearchOperationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SearchOperationProcessor.class);

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final DataStorage dataStorage;
    private final LdapNamingHelper namingHelper;

    private final Cache<Authentication, List<Entry>> entitiesCache;

    public SearchOperationProcessor(DataStorage dataStorage, LdapNamingHelper namingHelper, int searchCacheExpirationMinutes) {
        this.dataStorage = dataStorage;
        this.namingHelper = namingHelper;

        entitiesCache = CacheBuilder.newBuilder()
                .expireAfterAccess(searchCacheExpirationMinutes, TimeUnit.MINUTES)
                .build();
    }

    List<Entry> doSearch(Authentication authentication, SearchRequestProtocolOp request) throws LDAPException {

        List<Entry> allEntries;
        try {
            allEntries = entitiesCache.get(authentication, () -> doSearchInternal(authentication).stream()
                    .map(info -> prepareSearchResultEntry(info.get("dn").get(0), info))
                    .collect(Collectors.toList()));
        } catch (ExecutionException e) {
            // FIXME Обрабатывать или формировать ошибку более корректно. Возможно прикрутить лог.
            throw new RuntimeException(e.getCause());
        }

        List<Entry> resultEntries = new ArrayList<>(allEntries.size());
        SearchEntryParer parer = new SearchEntryParer(request.getAttributes(), null);
        for (Entry resultEntry : allEntries) {
            // Фильтруем записи в соответствие с запросом.
            if (resultEntry.matchesBaseAndScope(request.getBaseDN(), request.getScope()) &&
                    request.getFilter().matchesEntry(resultEntry)) {
                resultEntries.add(parer.pareEntry(resultEntry));
            } else {
                LOG.debug("Entry not matches {} to filter {}", resultEntry, request.getFilter());
            }
        }

        return resultEntries;
    }

    private List<Map<String, List<String>>> doSearchInternal(Authentication authentication) {
        List<Map<String, List<String>>> result = new ArrayList<>();

        dataStorage.getAllUsers(authentication).stream()
                 .map(user -> postProcessEntryInfo(user, EntityType.USER))
                .forEach(result::add);

        dataStorage.getAllGroups(authentication).stream()
                .map(group -> postProcessEntryInfo(group, EntityType.GROUP))
                .forEach(result::add);

        return result;
    }

    /**
     * Постобработка полученной из хранилища записи.
     * На текущий момент:
     * - добавляется dn атрибут, если нету
     * - преобразуется формат имени группы/участника в dn
     * - добавляется атрибут с классом объекта.
     */
    private Map<String, List<String>> postProcessEntryInfo(Map<String, List<String>> info, EntityType entityType) {
        // Оборачиваем результат, т.к. не уверены в возможности модифицировать пришедшие данные.
        var processedInfo = new HashMap<>(info);

        // Преобразуем имена групп/участников в dn
        addDnName(processedInfo, entityType);
        // Вычисляем dn если его не добавили ранее.
        processedInfo.computeIfAbsent("dn", s -> List.of(namingHelper.generateDnForEntry(info, entityType)));
        // Проставляем класс объекта, если его еще нет.
        processedInfo.computeIfAbsent("objectClass", s -> List.of(namingHelper.getClassName(entityType)));

        return processedInfo;
    }

    private void addDnName(Map<String, List<String>> info, EntityType entityType) {
        String key = entityType.equals(EntityType.USER) ? "memberof" : "member";

        List<String> result = info.get(key).stream()
                .map(value -> namingHelper.generateDnForEntryFromAttribute(value,
                        entityType.equals(EntityType.USER) ? EntityType.GROUP : EntityType.USER))
                .collect(Collectors.toList());
        info.put(key, result);
    }

    private Entry prepareSearchResultEntry(String entryDn, Map<String, List<String>> info) {
        // Подготовка ответа в формате ldap.
        Entry entry = new Entry(entryDn);
        for (String requestedAttributeName : info.keySet()) {
            final List<String> attributeValues = info
                    .getOrDefault(requestedAttributeName, Collections.emptyList());
            entry.addAttribute(requestedAttributeName, attributeValues.toArray(EMPTY_STRING_ARRAY));
        }

        return entry;
    }
}
