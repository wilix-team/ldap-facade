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

class UserAuthentication implements Authentication {
    private String userName;
    private String password;
    private boolean isSuccess;

    public UserAuthentication(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    public UserAuthentication(String userName, String password, boolean isSuccess) {
        this.userName = userName;
        this.password = password;
        this.isSuccess = isSuccess;
    }

    String getUserName() {
        return userName;
    }
    String getPassword() {
        return password;
    }
    @Override public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAuthentication that = (UserAuthentication) o;
        return userName.equals(that.userName) && password.equals(that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, password);
    }

    @Override
    public String toString() {
        return "UserAuthentication{" +
                "userName='" + userName + '\'' +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
