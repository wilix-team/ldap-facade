package dev.wilix.crm.ldap.model;

import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.AuthorizationIdentityResponseControl;
import com.unboundid.util.Debug;
import com.unboundid.util.StaticUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserBindAndSearchRequestHandler extends AllOpNotSupportedRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserBindAndSearchRequestHandler.class);

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    // TODO Сделать все это настройками.
    private static final Pattern DN_TO_USERNAME_PATTERN = Pattern.compile("uid=(.*),ou=people,dc=wilix,dc=dev");
    private static final Pattern DN_TO_SERVICENAME_PATTERN = Pattern.compile("uid=(.*),ou=services,dc=wilix,dc=dev");
    private static final Pattern SEARCH_FILTER_TO_USERNAME_PATTERN = Pattern.compile("\\(uid=(.+?)\\)");

    private static final String USER_DN_FROM_LOGIN_TEMPLATE = "uid=%s,ou=people,dc=wilix,dc=dev";

    private final LDAPListenerClientConnection connection;

    private final UserDataStorage userStorage;

    private Authentication authentication;


    /**
     * Для первичного создания обработчика.
     * При работе слушатель соединений будет использовать метод newInstance,
     * где уже присутствует экземпляр соединения.
     */
    public UserBindAndSearchRequestHandler(UserDataStorage userStorage) {
        this.userStorage = userStorage;
        this.connection = null;
    }

    protected UserBindAndSearchRequestHandler(LDAPListenerClientConnection connection, UserDataStorage userStorage) {
        this.connection = connection;
        this.userStorage = userStorage;
    }

    @Override
    public LDAPListenerRequestHandler newInstance(LDAPListenerClientConnection connection) {
        return new UserBindAndSearchRequestHandler(connection, userStorage);
    }

    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls) {

        LOG.info("Receive bind request: {}", request);

        if (request.getCredentialsType() != BindRequestProtocolOp.CRED_TYPE_SIMPLE) {
            LOG.warn("Not a simple request {}", request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_CREDENTIALS_INT_VALUE, null,
                    "Server supports only simple credentials.",
                    null, null));
        }

        if ((request.getSimplePassword() == null) || request.getSimplePassword().getValueLength() == 0) {
            LOG.warn("No password in request {}", request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_CREDENTIALS_INT_VALUE, null,
                    "The server has been configured to only allow bind operations that result in authenticated connections.  Anonymous bind operations are not allowed.",
                    null, null));
        }

        // Определение типа клиента (отдельный пользователь или сервис), поиск имени пользователя и выбор способа аутентификации.
        Function<String, Authentication> authenticator;
        try {
            if (isServiceBind(request)) {
                authenticator = password -> userStorage.authenticateService(extractServiceName(request), password);
            } else if (isUserBind(request)) {
                authenticator = password -> userStorage.authenticateUser(extractUserName(request), password);
            } else {
                throw new IllegalStateException("Unknown bind DN format");
            }
        } catch (Exception e) {
            LOG.warn("There is a problem with DN: {} for request: {}", e, request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_DN_SYNTAX_INT_VALUE, null,
                    "Not expected DN format",
                    null, null));
        }

        final String password = request.getSimplePassword().stringValue();

        if (StringUtils.isBlank(password)) {
            LOG.warn("Blank password in request {}", request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_CREDENTIALS_INT_VALUE, null,
                    "Username or password are wrong.",
                    null, null));
        }

        Authentication authResult;
        try {
            authResult = authenticator.apply(password);
        } catch (Exception e) {
            LOG.error("Errors occurred when authenticating", e);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.CONNECT_ERROR_INT_VALUE, request.getBindDN(),
                    String.format("Error in CRM request: %s", e.getMessage()),
                    null, null));
        }

        if ( ! authResult.isSuccess()) {
            LOG.warn("Bad auth result from CRM for request {}", request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_CREDENTIALS_INT_VALUE, null,
                    "Username or password are wrong.",
                    null, null));
        }

        LOG.info("There was a successful authentication {}.", authResult);

        // Как правило первой в соединении происходит аутентификация сервиса с токеном, а затем пользователей.
        // Поэтому присваивается только результат первой аутентификации.
        if (this.authentication == null) {
            this.authentication = authResult;
        }

        return new LDAPMessage(messageID,
                new BindResponseProtocolOp(ResultCode.SUCCESS_INT_VALUE, null,
                        null, null, null),
                List.of(new AuthorizationIdentityResponseControl("")));
    }

    private boolean isServiceBind(BindRequestProtocolOp request) {
        return DN_TO_SERVICENAME_PATTERN.matcher(request.getBindDN()).matches();
    }

    private boolean isUserBind(BindRequestProtocolOp request) {
        return DN_TO_USERNAME_PATTERN.matcher(request.getBindDN()).matches();
    }

    private String extractServiceName(BindRequestProtocolOp request) {
        String serviceName = null;
        Matcher matcher = DN_TO_SERVICENAME_PATTERN.matcher(request.getBindDN());
        if (matcher.matches()) {
            serviceName = matcher.group(1);
        }

        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("No service name in request");
        }

        return serviceName;
    }

    private String extractUserName(BindRequestProtocolOp request) {
        String userName = null;
        Matcher matcher = DN_TO_USERNAME_PATTERN.matcher(request.getBindDN());
        if (matcher.matches()) {
            userName = matcher.group(1);
        }

        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("No user name in request");
        }

        return userName;
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls) {
        LOG.info("Receive search request: {}", request);

        // Вытаскиваем имя пользователя из фильтра для поиска.
        String userName = extractUserNameFromSearchFilter(request.getFilter().toNormalizedString());
        if (userName == null || userName.isBlank()) {
            return new LDAPMessage(messageID, new SearchResultDoneProtocolOp(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS_INT_VALUE, null,
                    "No username in filter!",
                    null));
        }

        // Поиск атрибутов пользователя.
        Map<String, List<String>> info;
        try {
            info = userStorage.getInfo(userName, authentication);

            if (info == null || info.isEmpty()) {
                throw new IllegalStateException("No info for " + userName);
            }
        } catch (Exception e) {
            LOG.warn("There is problem with searching user info.", e);
            return new LDAPMessage(messageID, new SearchResultDoneProtocolOp(
                    ResultCode.NO_SUCH_OBJECT_INT_VALUE, null,
                    "User info not found!",
                    null));
        }

        // Подготовка ответа в формате ldap.
        Entry entry = new Entry(userName);
        for (String requestedAttributeName : request.getAttributes()) {
            final List<String> attributeValues = info
                    .getOrDefault(requestedAttributeName, Collections.emptyList());
            entry.addAttribute(requestedAttributeName, attributeValues.toArray(EMPTY_STRING_ARRAY));
        }

        // TODO Сделать более безопасным.
        entry.setDN(String.format(USER_DN_FROM_LOGIN_TEMPLATE, userName));

        // Отправка информации о пользователе.
        SearchResultEntry resultEntry = new SearchResultEntry(entry);
        try {
            connection.sendSearchResultEntry(messageID, resultEntry, resultEntry.getControls());
        } catch (final LDAPException le) {
            Debug.debugException(le);
            return new LDAPMessage(messageID,
                    new SearchResultDoneProtocolOp(le.getResultCode().intValue(),
                            le.getMatchedDN(), le.getDiagnosticMessage(),
                            StaticUtils.toList(le.getReferralURLs())),
                    le.getResponseControls());
        }

        // Успешное завершение операции.
        LOG.info("Search operation finished successfully with result {}", resultEntry);
        return new LDAPMessage(messageID,
                new SearchResultDoneProtocolOp(ResultCode.SUCCESS_INT_VALUE, null,
                        null, null),
                Collections.emptyList());
    }

    private String extractUserNameFromSearchFilter(String bindDN) {
        return regExpFindFirstGroup(SEARCH_FILTER_TO_USERNAME_PATTERN, bindDN);
    }

    private String regExpFindFirstGroup(Pattern regExpPattern, String valueToScan) {
        Matcher matcher = regExpPattern.matcher(valueToScan);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

}