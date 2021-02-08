package dev.wilix.ldap.facade.server.model;

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
 * Обработчик ldap запросов, который не обрабатывает ни одной операции.
 * Нужен для упрощения последующих дочерних классов.
 * FIXME Возвращать какой-нибудь корректный негативный ответ клиенту.
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