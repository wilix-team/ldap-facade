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
     *
     * @param serviceName Имя сервисной записи.
     * @param token       Токен для работы с API.
     * @return Результат аутентификации.
     */
    Authentication authenticateService(String serviceName, String token);

    /**
     * Получение списка пользователей с группами
     *
     * @param authentication Информация о текущей аутентификации пользователя\сервиса
     * @return Результат поиска
     */
    List<Map<String, List<String>>> getAllUsers(Authentication authentication);

    /**
     * Получение списка групп с пользователями
     *
     * @param authentication Информация о текущей аутентификации пользователя\сервиса
     * @return Результат поиска
     */
    List<Map<String, List<String>>> getAllGroups(Authentication authentication);

}