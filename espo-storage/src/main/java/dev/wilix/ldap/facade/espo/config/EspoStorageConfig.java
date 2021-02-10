package dev.wilix.ldap.facade.espo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.espo.EspoDataStorage;
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
    public DataStorage userDataStorage(EspoDataStorageConfigurationProperties config) {
        return new EspoDataStorage(httpClient(), objectMapper(), config);
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
