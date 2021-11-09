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

import com.unboundid.ldap.listener.LDAPListenerRequestHandler;
import com.unboundid.ldap.protocol.AddRequestProtocolOp;
import com.unboundid.ldap.protocol.BindRequestProtocolOp;
import com.unboundid.ldap.protocol.CompareRequestProtocolOp;
import com.unboundid.ldap.protocol.DeleteRequestProtocolOp;
import com.unboundid.ldap.protocol.ExtendedRequestProtocolOp;
import com.unboundid.ldap.protocol.LDAPMessage;
import com.unboundid.ldap.protocol.ModifyDNRequestProtocolOp;
import com.unboundid.ldap.protocol.ModifyRequestProtocolOp;
import com.unboundid.ldap.protocol.SearchRequestProtocolOp;
import com.unboundid.ldap.sdk.Control;

import java.util.List;

/**
 * Ldap requests handler, that does not process any operations.
 * Needed to simplify child classes.
 * FIXME Returns some correct answer to the client.
 */
public abstract class AllOpNotSupportedRequestHandler extends LDAPListenerRequestHandler {

    @Override
    public LDAPMessage processBindRequest(int messageID, BindRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Bind operation does not supported.");
    }

    @Override
    public LDAPMessage processSearchRequest(int messageID, SearchRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Search operation does not supported.");
    }

    @Override
    public LDAPMessage processAddRequest(int messageID, AddRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Add operation does not supported.");
    }

    @Override
    public LDAPMessage processCompareRequest(int messageID, CompareRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Compare operation does not supported.");
    }

    @Override
    public LDAPMessage processDeleteRequest(int messageID, DeleteRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Delete operation does not supported.");
    }

    @Override
    public LDAPMessage processExtendedRequest(int messageID, ExtendedRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Extended operation does not supported.");
    }

    @Override
    public LDAPMessage processModifyRequest(int messageID, ModifyRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("Modify operation does not supported.");
    }

    @Override
    public LDAPMessage processModifyDNRequest(int messageID, ModifyDNRequestProtocolOp request, List<Control> controls) {
        throw new IllegalStateException("DN modify operation does not supported.");
    }

}