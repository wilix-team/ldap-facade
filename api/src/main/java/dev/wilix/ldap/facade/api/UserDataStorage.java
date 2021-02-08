package dev.wilix.ldap.facade.api;

import java.util.List;
import java.util.Map;

/**
 * Интеграционный интерфейс для получения данных из внешнего источника.
 */
public interface UserDataStorage {

    /**
     * Прямая аутентификация пользователя.
     * @param userName Имя пользователя.
     * @param password Пароль пользователя.
     * @return Признак успешного входа пользователя.
     */
    Authentication authenticateUser(String userName, String password);

    /**
     * Аутентификация сервисного аккаунта.
     * @param serviceName
     * @param token
     * @return
     */
    Authentication authenticateService(String serviceName, String token);

    /**
     * Получение информации о пользователе.
     *
     * @param userName Логин пользователя, о котором требуется получить информацию.
     * @param authentication Информация о текущей аутентификации.
     * @return
     */
    Map<String, List<String>> getInfo(String userName, Authentication authentication);

}