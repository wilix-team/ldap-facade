import dev.wilix.ldap.facade.api.Authentication
import dev.wilix.ldap.facade.api.DataStorage

class GroovyWrongSyntax implements DataStorage{

    // Тут и должен быть +, все хорошо. Это тест неверного синтаксиса
    String + wrongSyntax() {
        return "123"
    }

    @Override
    Authentication authenticateUser(String userName, String password) {
        return null
    }

    @Override
    Authentication authenticateService(String serviceName, String token) {
        return null
    }

    @Override
    List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        return null
    }

    @Override
    List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        return null
    }
}
