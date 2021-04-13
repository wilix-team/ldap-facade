package dev.wilix.ldap.facade.api;

/**
 * Интерфейс для результатов аутентификации во внешнем сервисе.
 */
public interface Authentication {
    Authentication NEGATIVE = new Authentication() {
        @Override
        public boolean isSuccess() {
            return false;
        }
    };

    Authentication POSITIVE = new Authentication() {
        @Override
        public boolean isSuccess() {
            return true;
        }
    };

    boolean isSuccess();
}
