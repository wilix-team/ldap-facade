package dev.wilix.crm.ldap.model;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.BindResponseProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.protocol.SearchResultDoneProtocolOp;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.controls.AuthorizationIdentityResponseControl;
import com.unboundid.util.Debug;
import com.unboundid.util.StaticUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserBindAndSearchRequestHandler extends AllOpNotSupportedRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserBindAndSearchRequestHandler.class);

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final Pattern SEARCH_FILTER_TO_USERNAME_PATTERN = Pattern.compile("\\(uid=(.+?)\\)");

    private final LDAPListenerClientConnection connection;

    private final UserDataStorage userStorage;

    private String bindUser;
    private String bindPassword;


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

        final String username;
        try {
            username = request.getBindDN();

            if (username == null || username.isBlank()) {
                LOG.warn("No username in request {}", request);
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_DN_SYNTAX_INT_VALUE, null,
                    "No username in request",
                    null, null));
        }

        final ASN1OctetString bindPassword = request.getSimplePassword();
        final String password = bindPassword.stringValue();

        if (StringUtils.isBlank(password)) {
            LOG.warn("Blank password in request {}", request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_CREDENTIALS_INT_VALUE, null,
                    "Username or password are wrong.",
                    null, null));
        }

        boolean authResult;
        try {
            authResult = userStorage.authenticate(username, password);
        } catch (IOException | InterruptedException e) {
            LOG.error("Errors occurred when requesting CRM", e);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.CONNECT_ERROR_INT_VALUE, request.getBindDN(),
                    String.format("Error in CRM request: %s", e.getMessage()),
                    null, null));
        }

        if (!authResult) {
            LOG.warn("Bad auth result from CRM for request {}", request);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INVALID_CREDENTIALS_INT_VALUE, null,
                    "Username or password are wrong.",
                    null, null));
        }

        this.bindUser = username;
        this.bindPassword = password;
        LOG.info("User {} binded successfully.", username);

        return new LDAPMessage(messageID,
                new BindResponseProtocolOp(ResultCode.SUCCESS_INT_VALUE, null,
                        null, null, null),
                List.of(new AuthorizationIdentityResponseControl("")));
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls) {
        LOG.info("Receive search request: {}", request);

        // Вытаскиваем имя пользователя из фильтра для поиска.
        String username = extractUserNameFromSearchFilter(request.getFilter().toNormalizedString());
        if (username == null || username.isBlank()) {
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.INSUFFICIENT_ACCESS_RIGHTS_INT_VALUE, null,
                    "No username in filter!",
                    null, null));
        }

        // Поиск атрибутов пользователя.
        Map<String, List<String>> info = null;
        try {
            info = userStorage.getInfo(username, bindUser, bindPassword);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        if (info == null || info.isEmpty()) {
            LOG.warn("No info for {}", username);
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.NO_SUCH_OBJECT_INT_VALUE, null,
                    "Info not found!",
                    null, null));
        }

        // Подготовка ответа в формате ldap.
        Entry entry = new Entry(username);
        for (String requestedAttributeName : request.getAttributes()) {
            final List<String> attributeValues = info
                    .getOrDefault(requestedAttributeName, Collections.emptyList());
            entry.addAttribute(requestedAttributeName, attributeValues.toArray(EMPTY_STRING_ARRAY));
        }

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

        LOG.info("Search operation finished successfully with result {}", resultEntry);

        // Успешное завершение операции.
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