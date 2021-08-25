/*
 * Copyright 2021 WILIX LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.wilix.ldap.facade.espo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.wilix.ldap.facade.api.DataStorage;
import dev.wilix.ldap.facade.espo.AvatarHelper;
import dev.wilix.ldap.facade.espo.EntityParser;
import dev.wilix.ldap.facade.espo.EspoDataStorage;
import dev.wilix.ldap.facade.espo.RequestHelper;
import dev.wilix.ldap.facade.espo.config.properties.EspoDataStorageConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({EspoDataStorageConfigurationProperties.class})
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "espo")
public class EspoStorageConfig {

    @Autowired
    EspoDataStorageConfigurationProperties config;

    @Bean
    public DataStorage userDataStorage() {
        return new EspoDataStorage(requestHelper(), entityParser(), config.getCacheExpirationMinutes(), config.getBaseUrl());
    }

    @Bean
    public RequestHelper requestHelper() {
        return new RequestHelper(httpClient(), objectMapper());
    }

    @Bean
    public AvatarHelper avatarHelper() {
        return new AvatarHelper(config.getBaseUrl(), requestHelper());
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)) // TODO Возможно потребуется выносить в настройки.
                .build();
    }

    @Bean
    public EntityParser entityParser() {
        return new EntityParser(config.getAdditionalUserAttributes());
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
