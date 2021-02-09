package dev.wilix.ldap.facade.espo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.UserDataStorage;
import dev.wilix.ldap.facade.espo.EspoUserDataStorage;
import dev.wilix.ldap.facade.espo.config.properties.EspoDataStorageConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({EspoDataStorageConfigurationProperties.class})
public class EspoStorageConfig {

    @Bean
    public UserDataStorage userDataStorage(EspoDataStorageConfigurationProperties config) {
        return new EspoUserDataStorage(httpClient(), objectMapper(), config);
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)) // TODO Возможно потребуется выносить в настройки.
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
