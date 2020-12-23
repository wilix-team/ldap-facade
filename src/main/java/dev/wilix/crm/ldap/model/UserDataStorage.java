package dev.wilix.crm.ldap.model;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Интеграционный интерфейс для получения данных из внешнего источника.
 */
public interface UserDataStorage {

    /**
     * Прямая аутентификация пользователя.
     * @param username Имя пользователя.
     * @param password Пароль пользователя.
     * @return Признак успешного входа пользователя.
     * @throws IOException
     * @throws InterruptedException
     */
    boolean authenticate(String username, String password) throws IOException, InterruptedException;

    /**
     *
     * @param username
     * @param bindUser
     * @param bindPassword
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    Map<String, List<String>> getInfo(String username, String bindUser, String bindPassword) throws IOException, InterruptedException;

}