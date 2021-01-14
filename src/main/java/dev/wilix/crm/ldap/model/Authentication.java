package dev.wilix.crm.ldap.model;

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

    boolean isSuccess();
}
