package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Реализация хранилища пользователей и групп на основе файла.
 */
public class FileDataStorage implements DataStorage {

    private final FileParser fileParser;

    private final ReadWriteLock updateDataLock = new ReentrantReadWriteLock();
    private List<Map<String, List<String>>> users;
    private List<Map<String, List<String>>> groups;
    private Map<String, String> usersPasswordInfo;

    public FileDataStorage(FileParser fileParser) {
        this.fileParser = fileParser;
    }

    public void performParse(String fileContent) {
        final ParseResult parseResult = fileParser.parseFileContent(fileContent);
        usersPasswordInfo = new HashMap<>();

        Lock lock = updateDataLock.writeLock();
        lock.lock();
        try {
            users = parseResult.getUsers();
            groups = parseResult.getGroups();

            // Кладем информацию о пароле пользователя в отдельное хранилище
            // и удаляем пароли из основного, что-бы пароли не утекали наружу.
            for (Map<String, List<String>> user : users) {
                // TODO Безопасно брать поля из пользователя.
                usersPasswordInfo.put(user.get("uid").get(0), user.get("password").get(0));
                user.remove("password");
            }

        } finally {
            lock.unlock();
        }
    }

    @Override
    public FileUserAuthentication authenticateUser(String userName, String password) {
        FileUserAuthentication fileUserAuthentication = new FileUserAuthentication();
        fileUserAuthentication.setUserName(userName);

        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            if (usersPasswordInfo.containsKey(userName) && usersPasswordInfo.get(userName).equals(password)) {
                fileUserAuthentication.setSuccess(true);
                return fileUserAuthentication;
            }
        } finally {
            lock.unlock();
        }

        return fileUserAuthentication;
    }

    /**
     * Аутентификация сервисного аккаунта.
     *
     * В данном хранилище не используется сервисная аутентификация по причине того, что
     * никакой запрос на сервер не отправляется, а пользовательская аутентификация проходит
     * путём проверки наличия соответствующих логина и пароля пользователя в локальном файле формата json,
     * содержащем исключительно данные пользователей и групп.
     */
    @Override
    public Authentication authenticateService(String serviceName, String token) {
        return Authentication.NEGATIVE;
    }

    @Override
    public List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        if ( ! authentication.isSuccess()) {
            throw new IllegalStateException("Access denied");
        }

        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            return users;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        if ( ! authentication.isSuccess()) {
            throw new IllegalStateException("Access denied");
        }

        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            return groups;
        } finally {
            lock.unlock();
        }
    }
}
