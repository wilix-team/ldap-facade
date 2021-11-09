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

import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Class for processing authentication requests.
 */
public class BindOperationProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(BindOperationProcessor.class);

    private final DataStorage userStorage;
    private final LdapNamingHelper namingHelper;

    public BindOperationProcessor(DataStorage userStorage, LdapNamingHelper namingHelper) {
        this.userStorage = userStorage;
        this.namingHelper = namingHelper;
    }

    Authentication doBind(BindRequestProtocolOp request) throws LDAPException {
        if (request.getCredentialsType() != BindRequestProtocolOp.CRED_TYPE_SIMPLE) {
            LOG.warn("Not a simple request {}", request);
            throw new LDAPException(ResultCode.INVALID_CREDENTIALS,
                    "Server supports only simple credentials.");
        }

        if ((request.getSimplePassword() == null) || request.getSimplePassword().getValueLength() == 0) {
            LOG.warn("No password in request {}", request);
            throw new LDAPException(ResultCode.INVALID_CREDENTIALS,
                    "The server has been configured to only allow bind operations that result in authenticated connections.  Anonymous bind operations are not allowed.");
        }

        // Determining the type of client (user or service). Searching user name and select type of authentication.
        Function<String, Authentication> authenticator;
        try {
            String bindDn = request.getBindDN();
            if (namingHelper.isServiceDn(bindDn)) {
                authenticator = password -> {
                    String serviceName = namingHelper.extractServiceNameFromDn(bindDn);

                    if (serviceName == null || serviceName.isBlank()) {
                        throw new IllegalArgumentException("No service name in request");
                    }

                    return userStorage.authenticateService(serviceName, password);
                };
            } else if (namingHelper.isUserDn(bindDn)) {
                authenticator = password -> {
                    String userName = namingHelper.extractUserNameFromDn(bindDn);

                    if (userName == null || userName.isBlank()) {
                        throw new IllegalArgumentException("No user name in request");
                    }

                    return userStorage.authenticateUser(userName, password);
                };
            } else {
                throw new IllegalStateException("Unknown bind DN format");
            }
        } catch (Exception e) {
            LOG.warn("There is a problem with DN: {} for request: {}", e, request);
            throw new LDAPException(ResultCode.INVALID_DN_SYNTAX,
                    "Not expected DN format");
        }

        final String password = request.getSimplePassword().stringValue();

        if (StringUtils.isBlank(password)) {
            LOG.warn("Blank password in request {}", request);
            throw new LDAPException(ResultCode.INVALID_CREDENTIALS,
                    "Username or password are wrong.");
        }

        Authentication authResult;
        try {
            authResult = authenticator.apply(password);
        } catch (Exception e) {
            LOG.error("Errors occurred when authenticating", e);
            throw new LDAPException(ResultCode.CONNECT_ERROR,
                    String.format("Error with data storage: %s", e.getMessage()));
        }

        return authResult;
    }
}
