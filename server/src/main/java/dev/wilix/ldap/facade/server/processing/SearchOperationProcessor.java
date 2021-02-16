package dev.wilix.ldap.facade.server.processing;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResultEntry;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final Cache<Authentication, List<SearchResultEntry>> entitiesCache;

    public SearchOperationProcessor(DataStorage dataStorage, LdapNamingHelper namingHelper, int searchCacheExpirationMinutes) {
        this.dataStorage = dataStorage;
        this.namingHelper = namingHelper;

        entitiesCache = CacheBuilder.newBuilder()
                .expireAfterAccess(searchCacheExpirationMinutes, TimeUnit.MINUTES)
                .build();
    }

    List<SearchResultEntry> doSearch(Authentication authentication, SearchRequestProtocolOp request) throws LDAPException {

        // FIXME Обрабатывать или формировать ошибку более корректно. Возможно прикрутить лог.
        List<SearchResultEntry> allEntries;
        try {
            allEntries = entitiesCache.get(authentication, () -> doSearchInternal(authentication).stream()
                    .map(info -> prepareSearchResultEntry(info.get("dn").get(0), info))
                    .collect(Collectors.toList()));
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }

        List<SearchResultEntry> resultEntries = new ArrayList<>(allEntries.size());

        // FIXME Нужно как-то переработать, что-бы не отправлять лишние атрибуты.
        //       Но пока работает -_-
        for (SearchResultEntry resultEntry : allEntries) {
            // Фильтруем записи в соответствие с запросом.
            if (resultEntry.matchesBaseAndScope(request.getBaseDN(), request.getScope()) &&
                    request.getFilter().matchesEntry(resultEntry)) {
                resultEntries.add(resultEntry);
            } else {
                LOG.debug("Entry not matches {} to filter {}", resultEntry, request.getFilter());
            }
        }

        return resultEntries;
    }

    private List<Map<String, List<String>>> doSearchInternal(Authentication authentication) {
        List<Map<String, List<String>>> result = new ArrayList<>();

        dataStorage.getAllUsers(authentication).stream()
                .map(this::postProcessUserEntryInfo)
                .forEach(result::add);

        dataStorage.getAllGroups(authentication).stream()
                .map(this::postProcessGroupEntryInfo)
                .forEach(result::add);

        return result;
    }

    /**
     * Постобработка полученной из хранилища записи.
     * На текущий момент:
     *      - добавляется dn атрибут, если нету
     *      - преобразуется формат имени участников в dn
     *      - добавляется атрибут с классом объекта.
     */
    private Map<String, List<String>> postProcessUserEntryInfo(Map<String, List<String>> info) {
        // Оборачиваем результат, т.к. не уверены в возможности модифицировать пришедшие данные.
        var processedInfo = new HashMap<>(info);

        // Переводим имена участников в формат dn
        if (info.containsKey("memberof")) {
            // Переводим формат из имени группы в dn группы.
            List<String> memberOfWithDn = info.get("memberof").stream()
                    .map(namingHelper::generateDnForGroupEntryFromAttribute)
                    .collect(Collectors.toList());
            processedInfo.put("memberof", memberOfWithDn);
        }

        // Вычисляем dn если его не добавили ранее.
        processedInfo.computeIfAbsent("dn", s -> List.of(namingHelper.generateDnForUserEntry(info)));
        // Проставляем класс объекта, если его еще нет.
        processedInfo.computeIfAbsent("objectClass", s -> List.of(namingHelper.getUserClassName()));
        processedInfo.computeIfAbsent("objectclass", s -> List.of(namingHelper.getUserClassName()));

        return processedInfo;
    }

    /**
     * Постобработка полученной из хранилища записи.
     * На текущий момент:
     *      - добавляется dn атрибут, если нету
     *      - преобразуется формат имени участников в dn
     *      - добавляется атрибут с классом объекта.
     */
    private Map<String, List<String>> postProcessGroupEntryInfo(Map<String, List<String>> info) {
        // Оборачиваем результат, т.к. не уверены в возможности модифицировать пришедшие данные.
        var processedInfo = new HashMap<>(info);

        if (info.containsKey("member")) {
            // Переводим формат из имени группы в dn группы.
            List<String> memberOfWithDn = info.get("member").stream()
                    .map(namingHelper::generateDnForUserEntryFromAttribute)
                    .collect(Collectors.toList());
            processedInfo.put("member", memberOfWithDn);
        }

        // Вычисляем dn если его не добавили ранее.
        processedInfo.computeIfAbsent("dn", s -> List.of(namingHelper.generateDnForGroupEntry(info)));
        // Проставляем класс объекта, если его еще нет.
        processedInfo.computeIfAbsent("objectClass", s -> List.of(namingHelper.getGroupClassName()));
        processedInfo.computeIfAbsent("objectclass", s -> List.of(namingHelper.getGroupClassName()));

        return processedInfo;
    }

    private SearchResultEntry prepareSearchResultEntry(String entryDn, Map<String, List<String>> info) {
        // Подготовка ответа в формате ldap.
        Entry entry = new Entry(entryDn);
        for (String requestedAttributeName : info.keySet()) {
            final List<String> attributeValues = info
                    .getOrDefault(requestedAttributeName, Collections.emptyList());
            entry.addAttribute(requestedAttributeName, attributeValues.toArray(EMPTY_STRING_ARRAY));
        }

        return new SearchResultEntry(entry);
    }
}
