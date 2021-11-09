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

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Class for tracking changes in any file.
 */
public class FileWatcher {
    private final static Logger LOGGER = LoggerFactory.getLogger(FileWatcher.class);

    private final Path pathToFile;
    private final int fileWatchInterval;
    private final Consumer<String> fileContentListener;
    private final String fileName;

    public FileWatcher(Path pathToFile, int watchIntervalMillis, Consumer<String> fileContentListener) {
        if ( ! Files.exists(pathToFile)) {
            throw new IllegalStateException("File at the specified path does not exist.");
        }

        this.pathToFile = pathToFile;
        this.fileWatchInterval = watchIntervalMillis;
        this.fileContentListener = fileContentListener;
        this.fileName = pathToFile.getFileName().toString(); // TODO Consider removing this field.
    }

    public void watchFileChanges() {
        watch(createObserver());
    }

    /**
     * Preparing a tracker for a file.
     */
    private FileAlterationObserver createObserver() {
        String directory = pathToFile.getParent().toString();
        FileAlterationObserver observer = new FileAlterationObserver(directory, createFilter());
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onFileChange(File file) {
                LOGGER.info("Receive file " + fileName + " change event.");
                FileWatcher.this.processChangeEvent();
            }

        });
        return observer;
    }

    private IOFileFilter createFilter() {
        return FileFilterUtils.and(
                FileFilterUtils.fileFileFilter(),
                FileFilterUtils.suffixFileFilter(fileName));
    }

    /**
     * Handling the file change event.
     */
    private void processChangeEvent() {
        String fileContent;
        try {
            fileContent = Files.readString(pathToFile);
        } catch (IOException e) {
            throw new IllegalStateException("Can't read watched wile!", e);
        }
        fileContentListener.accept(fileContent);
    }

    /**
     * Start tracking a file.
     */
    private void watch(FileAlterationObserver observer){
        FileAlterationMonitor monitor = new FileAlterationMonitor(fileWatchInterval, observer);

        try {
            LOGGER.info("Starting to listen " + fileName + " file");
            monitor.start();
        } catch (InterruptedException e) {
            LOGGER.error("The operation was interrupted: ", e);
            throw new IllegalStateException("The operation was interrupted: ", e);
        } catch (Exception e) {
            LOGGER.error("Problem with watching file:  ", e);
            throw new IllegalStateException("Problem with watching file:  ", e);
        }
    }
}
