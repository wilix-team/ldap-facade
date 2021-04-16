package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;
import dev.wilix.ldap.facade.api.DataStorage;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileDataStorage implements DataStorage {

    private final FileParser fileParser;

    private final ReadWriteLock updateDataLock = new ReentrantReadWriteLock();
    private List<Map<String, List<String>>> users;
    private List<Map<String, List<String>>> groups;

    public FileDataStorage(ParseResult initialState, FileParser fileParser) {
        this.fileParser = fileParser;
    }

    public void performParse(String fileContent) {
        final ParseResult parseResult = fileParser.parseFileContent(fileContent);

        Lock lock = updateDataLock.writeLock();
        lock.lock();
        try {
            users = parseResult.getUsers();
            groups = parseResult.getGroups();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Authentication authenticateUser(String userName, String password) {
        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            for (Map<String, List<String>> user : users) {
                if (user.get("uid").get(0).equals(userName) && user.get("password").get(0).equals(password)) {
                    return Authentication.POSITIVE;
                }
            }
        } finally {
            lock.unlock();
        }

        return Authentication.NEGATIVE;
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
        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            if (authentication.isSuccess()) {
                return users;
            }
        } finally {
            lock.unlock();
        }

        throw new IllegalStateException("Access denied");
    }

    @Override
    public List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        Lock lock = updateDataLock.readLock();
        lock.lock();
        try {
            if (authentication.isSuccess()) {
                return groups;
            }
        } finally {
            lock.unlock();
        }

        throw new IllegalStateException("Access denied");
    }
}
