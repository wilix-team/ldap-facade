package dev.wilix.crm.ldap.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Интеграционный интерфейс для получения данных из внешнего источника.
 */
public interface UserDataStorage {

    boolean authenticate(String username, String password) throws IOException, InterruptedException;

    Map<String, List<String>> getInfo(String username, String bindUser, String bindPassword) throws IOException, InterruptedException;

}