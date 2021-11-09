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

package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.api.Authentication;

/**
 * Class for store successful user authentication from a file.
 */
class FileUserAuthentication implements Authentication {
    private String userName;
    private boolean isSuccess;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    @Override
    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public String toString() {
        return "FileUserAuthentication{" +
                "userName='" + userName + '\'' +
                ", isSuccess=" + isSuccess +
                '}';
    }
}
