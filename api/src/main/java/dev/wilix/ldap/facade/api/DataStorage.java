package dev.wilix.ldap.facade.api;

import java.util.List;
import java.util.Map;

/**
 * Интеграционный интерфейс для получения данных из внешнего источника.
 */
public interface DataStorage {

    /**
     * Прямая аутентификация пользователя.
     * @param userName Имя пользователя.
     * @param password Пароль пользователя.
     * @return Результат аутентификации.
     */
    Authentication authenticateUser(String userName, String password);

    /**
     * Аутентификация сервисного аккаунта.
     * @param serviceName Имя сервисной записи.
     * @param token Токен для работы с API.
     * @return Результат аутентификации.
     */
    Authentication authenticateService(String serviceName, String token);

    /**
     * Получение информации о пользователе.
     *
     * @param userName Логин пользователя, о котором требуется получить информацию.
     * @param authentication Информация о текущей аутентификации.
     * @return Информация об одном пользователе
     */
    Map<String, List<String>> getSingleUserInfo(String userName, Authentication authentication);

    /**
     * Получение информации о нескольких пользователях
     * на основе ldap-шаблона (с применением подстановочного символа '*')
     *
     * @param template Шаблон для выборки пользователей.
     * @param authentication Информация о текущей аутентификации.
     * @return Информация о списке пользователей.
     */
    List<Map<String, List<String>>> getUserInfoByTemplate(String template, Authentication authentication);

    Map<String, List<String>> getSingleGroup(String groupName, Authentication authentication);

    List<Map<String, List<String>>> getAllGroups(Authentication authentication);

}