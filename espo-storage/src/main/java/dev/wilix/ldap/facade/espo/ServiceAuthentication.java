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

package dev.wilix.ldap.facade.espo;


import dev.wilix.ldap.facade.api.Authentication;

import java.util.Objects;

/**
 * The result of the service account authentication in CRM.
 */
class ServiceAuthentication implements Authentication {
    private final String serviceName;
    private final String token;
    private final boolean isSuccess;

    public ServiceAuthentication(String serviceName, String token) {
        this(serviceName, token, false);
    }

    public ServiceAuthentication(String serviceName, String token, boolean isSuccess) {
        this.serviceName = serviceName;
        this.token = token;
        this.isSuccess = isSuccess;
    }

    String getServiceName() { return serviceName; }
    String getToken() { return token; }
    @Override public boolean isSuccess() { return isSuccess; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceAuthentication that = (ServiceAuthentication) o;
        return serviceName.equals(that.serviceName) && token.equals(that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, token);
    }

    @Override
    public String toString() {
        return "ServiceAuthentication{" +
                "serviceName='" + serviceName + '\'' +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
