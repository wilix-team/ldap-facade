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

import com.unboundid.ldap.listener.LDAPListenerClientConnection;
import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.ldap.protocol.*;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.controls.AuthorizationIdentityResponseControl;
import com.unboundid.util.StaticUtils;
import dev.wilix.ldap.facade.api.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class UserBindAndSearchRequestHandler extends AllOpNotSupportedRequestHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UserBindAndSearchRequestHandler.class);

    private final LDAPListenerClientConnection connection;

    // Обработчики запросов.
    private final BindOperationProcessor bindOperationProcessor;
    private final SearchOperationProcessor searchOperationProcessor;

    // Информация о аутентифицированном пользователе.
    private Authentication authentication;

    /**
     * Для первичного создания обработчика.
     * При работе слушатель соединений будет использовать метод newInstance,
     * где уже присутствует экземпляр соединения.
     */
    public UserBindAndSearchRequestHandler(BindOperationProcessor bindOperationProcessor,
                                           SearchOperationProcessor searchOperationProcessor) {
        this(null, bindOperationProcessor, searchOperationProcessor);
    }

    protected UserBindAndSearchRequestHandler(LDAPListenerClientConnection connection,
                                              BindOperationProcessor bindOperationProcessor,
                                              SearchOperationProcessor searchOperationProcessor) {
        this.connection = connection;
        this.bindOperationProcessor = bindOperationProcessor;
        this.searchOperationProcessor = searchOperationProcessor;
    }

    @Override
    public LDAPListenerRequestHandler newInstance(LDAPListenerClientConnection connection) {
        return new UserBindAndSearchRequestHandler(connection, bindOperationProcessor, searchOperationProcessor);
    }

    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls) {

        LOG.info("Receive bind request: {}", request);

        final Authentication authResult;
        try {
            authResult = bindOperationProcessor.doBind(request);
        } catch (LDAPException ex) {
            return new LDAPMessage(messageID,
                    new BindResponseProtocolOp(ex.getResultCode().intValue(),
                            ex.getMatchedDN(), ex.getDiagnosticMessage(),
                            StaticUtils.toList(ex.getReferralURLs()), null),
                    ex.getResponseControls());
        } catch (Exception ex) {
            LOG.warn("Bind operation [{}] interrupted with not expected error: {}", request, ex);
            // FIXME Возможно другое сообщение...
            return new LDAPMessage(messageID, new BindResponseProtocolOp(
                    ResultCode.OTHER_INT_VALUE, null,
                    "Unexpected errors occurred!",
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

        // Как правило, первой в соединении происходит аутентификация сервиса с токеном, а затем пользователей.
        // Поэтому присваивается только результат первой аутентификации.
        if (this.authentication == null) {
            this.authentication = authResult;
        }

        return new LDAPMessage(messageID,
                new BindResponseProtocolOp(ResultCode.SUCCESS_INT_VALUE, null,
                        null, null, null),
                List.of(new AuthorizationIdentityResponseControl("")));
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls) {
        LOG.info("Receive search request: {}", request);

        if (authentication == null || !authentication.isSuccess()) {
            return new LDAPMessage(messageID,
                    new SearchResultDoneProtocolOp(ResultCode.INVALID_CREDENTIALS_INT_VALUE,
                            null, "Incorrect credentials or access rights.", null),
                    Collections.emptyList());
        }

        List<SearchResultEntry> foundedEntries;
        try {
            foundedEntries = searchOperationProcessor.doSearch(authentication, request);

            if (foundedEntries == null) {
                throw new IllegalStateException("Unexpected empty result from search.");
            }
        } catch (LDAPException ex) {
            LOG.warn("End search operation [{}] with expected error: {}", request, ex);
            return new LDAPMessage(messageID,
                    new SearchResultDoneProtocolOp(ex.getResultCode().intValue(),
                            ex.getMatchedDN(), ex.getDiagnosticMessage(),
                            StaticUtils.toList(ex.getReferralURLs())),
                    ex.getResponseControls());
        } catch (Exception ex) {
            LOG.warn("Search operation [{}] interrupted with not expected error: {}", request, ex);
            // FIXME Возможно другое сообщение...
            return new LDAPMessage(messageID, new SearchResultDoneProtocolOp(
                    ResultCode.OTHER_INT_VALUE, null,
                    "Unexpected errors occured!",
                    null));
        }

        // Отправка информации о записях.
        for (SearchResultEntry resultEntry : foundedEntries) {
            try {
                connection.sendSearchResultEntry(messageID, resultEntry, resultEntry.getControls());
            } catch (final LDAPException ex) {
                LOG.warn("There is problems with send result ldap entry.", ex);
                return new LDAPMessage(messageID,
                        new SearchResultDoneProtocolOp(ex.getResultCode().intValue(),
                                ex.getMatchedDN(), ex.getDiagnosticMessage(),
                                StaticUtils.toList(ex.getReferralURLs())),
                        ex.getResponseControls());
            }
        }

        // Успешное завершение операции.
        LOG.info("Search operation finished successfully with results {}", foundedEntries);
        return new LDAPMessage(messageID,
                new SearchResultDoneProtocolOp(ResultCode.SUCCESS_INT_VALUE, null,
                        null, null),
                Collections.emptyList());
    }
}