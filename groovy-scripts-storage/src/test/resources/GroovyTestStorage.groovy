import dev.wilix.ldap.facade.api.Authentication
import dev.wilix.ldap.facade.api.DataStorage

class GroovyTestStorage implements DataStorage {

    def users = [
        [
            id: ["1"],
            userName: ["username"],
            password: ["password"],
            phoneNumber: ["554611"],
            emailAddress: ["addres@ad.ru"],
            name: ["vcsName"]
        ],
        [
            id: ["2"],
            userName: ["username2"],
            password: ["password2"],
            phoneNumber: ["22222"],
            emailAddress: ["addres2@ad.ru"],
            name: ["vcsName2"]
        ]
    ]

    def groups = [
        [
            id: ["1"],
            name: ["test"],
            member: ["username", "username2"]
        ],
        [
            id: ["2"],
            name: ["test2"],
            member: ["username", "username2"]
        ],
        [
            id: ["3"],
            name: ["employee"],
            member: ["username"]
        ]
    ]

    @Override
    Authentication authenticateUser(String userName, String password) {
        boolean isPresent = users.any(user ->
                user.get("userName").get(0) == userName && user.get("password").get(0) == password)

        return isPresent ? Authentication.POSITIVE : Authentication.NEGATIVE
    }

    @Override
    Authentication authenticateService(String serviceName, String token) {
        return Authentication.NEGATIVE
    }

    @Override
    List<Map<String, List<String>>> getAllUsers(Authentication authentication) {
        return users
    }

    @Override
    List<Map<String, List<String>>> getAllGroups(Authentication authentication) {
        return groups
    }
}
