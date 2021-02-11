package dev.wilix.ldap.facade.server.processing;

import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.*;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

        List<Map<String, List<String>>> result = new ArrayList<>();

        if (isSingleUserRequest(request)) {
            // Вытаскиваем имя пользователя из фильтра для поиска.
            String userName = extractUserNameFromSearchRequest(request);
            if (userName == null || userName.isBlank()) {
                LOG.warn("Can't extract user name from request {}", request);
                throw new LDAPException(
                        ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                        "Can't detect user name from request!");
            }

            result.add(getSingleUserEntry(authentication, userName));
        }

        if (isUserPatternRequest(request)) {
            String userTemplate = namingHelper.extractEntryNamePatternFromSearchFilter(request.getFilter().toNormalizedString());
            dataStorage.getUserInfoByTemplate(userTemplate, authentication).stream()
            .map(this::postProcessUserEntryInfo)
            .forEach(result::add);
        }

        return result;
    }

    /**
     * Проверка, что запрашивается один конкретный пользователь.
     */
    private boolean isSingleUserRequest(SearchRequestProtocolOp request) {
        // Поиск по поддереву точно не попадает под условия поиска одной записи.
        if (SearchScope.ONE.equals(request.getScope()) ||
                SearchScope.SUBORDINATE_SUBTREE.equals(request.getScope())) {
//            return false; // FIXME Нужно решить, насколько придерживаться протокола.
        }

        String classNameFilter = namingHelper.extractObjectClassNamePatternFromSearchFilter(request.getFilter().toNormalizedString());
        if (StringUtils.isNotBlank(classNameFilter) &&
                !namingHelper.isWildcardPattern(classNameFilter) &&
                !namingHelper.getUserClassName().equals(classNameFilter)) {
            return false;
        }

        // Если в фильтре поиска указано конкретное значение, а не шаблон.
        String filterNameValue = namingHelper.extractEntryNamePatternFromSearchFilter(request.getFilter().toNormalizedString());
        if (StringUtils.isNotBlank(filterNameValue) &&
                 ! namingHelper.isWildcardPattern(filterNameValue)) {
            return true;
        }

        // Если производится запрос по точному местонахождению записи пользвателя.
        return namingHelper.isUserDn(request.getBaseDN());
    }

    private boolean isUserPatternRequest(SearchRequestProtocolOp request) {
        // Если ищется только одна запись или все поддерево, включая базовую запись,
        //  то это не поиск по шаблону.
        if (SearchScope.BASE.equals(request.getScope()) ||
                SearchScope.SUBORDINATE_SUBTREE.equals(request.getScope())) {
            return false;
        }

        // Если мы не ищем в одном из вышестоящих ветвей пути к пользователям,
        // то это не поиск по шаблону.
        if ( ! (namingHelper.isBaseDn(request.getBaseDN()) ||
                namingHelper.isUsersBaseDn(request.getBaseDN()))) {
            return false;
        }

        // Если у нас указано местоположение конкретного пользователя,
        // то это не поиск по шаблону.
        if (namingHelper.isUserDn(request.getBaseDN())) {
            return false;
        }

        String requestFilter = request.getFilter().toNormalizedString();

        // FIXME Придумать, что делать с запросами на класс записи.
        String classNameFilter = namingHelper.extractObjectClassNamePatternFromSearchFilter(requestFilter);
        if (StringUtils.isNotBlank(classNameFilter) &&
                !namingHelper.isWildcardPattern(classNameFilter) &&
                !namingHelper.getUserClassName().equals(classNameFilter)) {
            return false;
        }

        // Если в запросе есть фильтр на базовый атрибут имени и он содержит подстановочные символы.
        String filterNameValue = namingHelper.extractEntryNamePatternFromSearchFilter(requestFilter);
        return StringUtils.isNotBlank(filterNameValue) &&
                namingHelper.isWildcardPattern(filterNameValue);
    }

    private Map<String, List<String>> getSingleUserEntry(Authentication authentication, String userName) throws LDAPException {
        // Поиск атрибутов пользователя.
        Map<String, List<String>> info;
        try {
            info = dataStorage.getSingleUserInfo(userName, authentication);

            if (info == null || info.isEmpty()) {
                throw new IllegalStateException("No info for " + userName);
            }

            info = postProcessUserEntryInfo(info);
        } catch (Exception e) {
            LOG.warn("There is problem with searching user info.", e);
            throw new LDAPException(ResultCode.NO_SUCH_OBJECT, "Can't find entry!", e);
        }
        return info;
    }

    /**
     * Постобработка полученной из хранилища записи.
     * На текущий момент добавляется dn атрибут, если нету
     */
    private Map<String, List<String>> postProcessUserEntryInfo(Map<String, List<String>> info) {
        // Оборачиваем результат, т.к. не уверены в возможности модифицировать пришедшие данные.
        var processedInfo = new HashMap<>(info);

        // Вычисляем dn если его не добавили ранее.
        processedInfo.computeIfAbsent("dn", s -> List.of(namingHelper.generateDnForUserEntry(info)));
        // Проставляем класс объекта, если его еще нет.
        processedInfo.computeIfAbsent("objectClass", s -> List.of(namingHelper.getUserClassName()));

        return processedInfo;
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
        String result = null;

        // Ищется только одна запись по конкретному DN.
        if (request.getScope() == SearchScope.BASE) {
            return namingHelper.extractUserNameFromDn(request.getBaseDN());
        }

        // Когда ищется сам элемент и все его поддерево, то имя пользователя тоже может содержаться в DN.
        if (request.getScope() == SearchScope.SUB) {
            result = namingHelper.extractUserNameFromDn(request.getBaseDN());
        }

        // Смотрим в фильтр и пытаемся достать ожидаемое имя пользователя оттуда.
        if (result == null) {
            result = namingHelper.extractEntryNamePatternFromSearchFilter(request.getFilter().toNormalizedString());
        }

        return result;
    }
}
