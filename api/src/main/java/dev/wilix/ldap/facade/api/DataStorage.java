package dev.wilix.ldap.facade.api;

import java.util.List;
import java.util.Map;

/**
 * Интеграционный интерфейс для получения данных из внешнего источника.
 */
public interface DataStorage {

    /**
     * Прямая аутентификация пользователя.
     *
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

    // TODO Написать документацию для методов.

    /**
     * Получение списка пользователей с группами
     * @param authentication Имя сервисной записи.
     * @return Результат поиска
     */
    List<Map<String, List<String>>> getAllUsers(Authentication authentication);

    /**
     * Получение списка групп с пользователями
     * @param authentication Имя сервисной записи.
     * @return Результат поиска
     */
    List<Map<String, List<String>>> getAllGroups(Authentication authentication);

}