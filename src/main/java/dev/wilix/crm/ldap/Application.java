package dev.wilix.crm.ldap;

import com.unboundid.ldap.listener.LDAPListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class Application implements CommandLineRunner {

	@Autowired
	LDAPListener listener;

	private static Logger LOG = LoggerFactory.getLogger(Application.class);

	public static void main(String[] args) throws Exception {
		SpringApplication.run(Application.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		LOG.info("LDAP FACADE STARTING....");

		listener.startListening();

		LOG.info(String.format("LDAP FACADE LISTENING ON PORT %s....", listener.getListenPort()));
	}
}
