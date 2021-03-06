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

package dev.wilix.ldap.facade.file.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "storage.file")
public class FileStorageConfigurationProperties {

    @NotNull
    private Path pathToFile;

    @NotNull
    @Positive
    private Integer fileWatchInterval = 10_000;

    public Path getPathToFile() {
        return this.pathToFile;
    }

    public void setPathToFile(Path pathToFile) {
        this.pathToFile = pathToFile;
    }

    public Integer getFileWatchInterval() {
        return fileWatchInterval;
    }

    public void setFileWatchInterval(Integer fileWatchInterval) {
        this.fileWatchInterval = fileWatchInterval;
    }
}
