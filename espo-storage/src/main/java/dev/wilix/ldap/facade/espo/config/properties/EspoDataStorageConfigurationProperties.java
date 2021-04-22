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

package dev.wilix.ldap.facade.espo.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

@Validated // TODO Добавить зависимость и навесить валидацию для полей.
@ConfigurationProperties(prefix = "storage.espo")
public class EspoDataStorageConfigurationProperties {
    private int cacheExpirationMinutes = 2;

    @NotEmpty
    private String baseUrl;

    @NotEmpty
    private Map<String, List<String>> additionalUserInformationTags;

    public int getCacheExpirationMinutes() {
        return cacheExpirationMinutes;
    }

    public void setCacheExpirationMinutes(int cacheExpirationMinutes) {
        this.cacheExpirationMinutes = cacheExpirationMinutes;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, List<String>> getAdditionalUserInformationTags() {
        return additionalUserInformationTags;
    }

    public void setAdditionalUserInformationTags(Map<String, List<String>> additionalUserInformationTags) {
        this.additionalUserInformationTags = additionalUserInformationTags;
    }
}
