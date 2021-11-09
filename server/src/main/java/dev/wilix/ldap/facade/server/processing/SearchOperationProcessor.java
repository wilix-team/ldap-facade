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
 * TODO Move names of member and memberOf attributes to ldap configuration.
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
                .expireAfterWrite(searchCacheExpirationMinutes, TimeUnit.MINUTES)
                .build();
    }

    List<Entry> doSearch(Authentication authentication, SearchRequestProtocolOp request) throws LDAPException {

        List<Entry> allEntries;
        try {
            allEntries = entitiesCache.get(authentication, () -> doSearchInternal(authentication).stream()
                    .map(info -> prepareSearchResultEntry(info.get("dn").get(0), info))
                    .collect(Collectors.toList()));
        } catch (ExecutionException e) {
            // FIXME Handle or generate exception more correctly. Maybe add a log.
            throw new RuntimeException(e.getCause());
        }

        List<Entry> resultEntries = new ArrayList<>(allEntries.size());
        SearchEntryParer parer = new SearchEntryParer(request.getAttributes(), null);
        for (Entry resultEntry : allEntries) {
            // Filtering records according to the request.
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
     * Post-processing of the record received from the storage.
     * Steps of post-processing at this moment:
     * - Adding dn attribute, if it does not exist
     * - Converts the group / member name format to dn
     * - Adding object class attribute.
     */
    private Map<String, List<String>> postProcessEntryInfo(Map<String, List<String>> info, EntityType entityType) {
        // Wrapping result, because info may be immutable.
        var processedInfo = new HashMap<>(info);

        // Converts the group / member name format to dn
        addDnName(processedInfo, entityType);
        // Computes dn, if it does not exist.
        processedInfo.computeIfAbsent("dn", s -> List.of(namingHelper.generateDnForEntry(info, entityType)));
        // Adding object class attribute, if it does not exist.
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
        // Preparing ldap answer.
        Entry entry = new Entry(entryDn);
        for (String requestedAttributeName : info.keySet()) {
            final List<String> attributeValues = info
                    .getOrDefault(requestedAttributeName, Collections.emptyList());
            entry.addAttribute(requestedAttributeName, attributeValues.toArray(EMPTY_STRING_ARRAY));

            String userAvatarAttributeName = "jpegPhoto";
            if (info.containsKey(userAvatarAttributeName)) {
                byte[] avatarByteArray = Base64.getDecoder().decode(info.get(userAvatarAttributeName).get(0));
                entry.setAttribute(userAvatarAttributeName, avatarByteArray);
            }
        }
        return entry;
    }
}
