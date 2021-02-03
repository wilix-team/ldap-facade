package dev.wilix.crm.ldap;

import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.LDAPTestUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Disabled")
public class RealTest {

    @Test
    @Disabled("Для реального вызова")
    public void disabled() throws LDAPException {

        LDAPConnection connection = new LDAPConnection("localhost", 10636);

        BindResult result = connection.bind("uid=svetlana.okuneva,ou=people,dc=wilix,dc=dev", "pwd");

        LDAPTestUtils.assertResultCodeEquals(result, ResultCode.SUCCESS);
        System.out.println(result);
        connection.close();
    }
}
