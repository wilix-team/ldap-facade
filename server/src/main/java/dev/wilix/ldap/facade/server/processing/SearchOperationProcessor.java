package dev.wilix.ldap.facade.server.processing;

import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResultEntry;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SearchOperationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SearchOperationProcessor.class);

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final DataStorage dataStorage;
    private final LdapNamingHelper namingHelper;

    public SearchOperationProcessor(DataStorage dataStorage, LdapNamingHelper namingHelper) {
        this.dataStorage = dataStorage;
        this.namingHelper = namingHelper;
    }

    List<SearchResultEntry> doSearch(Authentication authentication, SearchRequestProtocolOp request) throws LDAPException {
        var infos = doSearchInternal(authentication, request);

        List<SearchResultEntry> resultEntries = new ArrayList<>(infos.size());

        for (Map<String, List<String>> info : infos) {
            SearchResultEntry resultEntry = prepareSearchResultEntry(info.get("dn").get(0), info, request.getAttributes());
            resultEntries.add(resultEntry);
        }

        return resultEntries;
    }

    private List<Map<String, List<String>>> doSearchInternal(Authentication authentication, SearchRequestProtocolOp request) throws LDAPException {
        // TODO Порядок действий для поиска сущностей:
        //      1. Нужно определить, какие сущности нужны и в каком количестве.
        //      2. Для этого нужно как на атрибут уникального имени смотреть, так и на имя запрашиваемого класса.
        //      3. После этого нужно сходить и запросить у хранилища одну конкретную или несколько записей
        //      (могут быть ситуации, когда нужно и пользователей и группы возвращать)

        // TODO Генерировать dn сущности, сразу после запроса.

        // Вытаскиваем имя пользователя из фильтра для поиска.
        String userName = extractUserNameFromSearchRequest(request);
        if (userName == null || userName.isBlank()) {
            LOG.warn("Can't extract user name from request {}", request);
            throw new LDAPException(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                    "No username in filter!");
        }

        // Поиск атрибутов пользователя.
        Map<String, List<String>> info;
        try {
            info = dataStorage.getSingleUserInfo(userName, authentication);

            if (info == null || info.isEmpty()) {
                throw new IllegalStateException("No info for " + userName);
            }

            // Вычисляем dn если его не добавили ранее.
            info.computeIfAbsent("dn", s -> List.of(namingHelper.generateDnForUserEntry(info)));
        } catch (Exception e) {
            LOG.warn("There is problem with searching user info.", e);
            throw new LDAPException(ResultCode.NO_SUCH_OBJECT, "User info not found!", e);
        }

        return List.of(info);
    }

    private SearchResultEntry prepareSearchResultEntry(String entryDn, Map<String, List<String>> info, List<String> attributes) {
        // Подготовка ответа в формате ldap.
        Entry entry = new Entry(entryDn);
        for (String requestedAttributeName : attributes) {
            final List<String> attributeValues = info
                    .getOrDefault(requestedAttributeName, Collections.emptyList());
            entry.addAttribute(requestedAttributeName, attributeValues.toArray(EMPTY_STRING_ARRAY));
        }

        return new SearchResultEntry(entry);
    }

    private String extractUserNameFromSearchRequest(SearchRequestProtocolOp request) {
        String result;

        result = namingHelper.extractEntryNameFromSearchFilter(request.getFilter().toNormalizedString());

        if (result == null) {
            result = namingHelper.extractServiceNameFromDn(request.getBaseDN());
        }

        return result;
    }
}
