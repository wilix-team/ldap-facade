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
 * Класс для отслеживания изменений в каком-либо файле.
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
        this.fileName = pathToFile.getFileName().toString(); // TODO Рассмотреть возможность удаления этого поля.
    }

    public void watchFileChanges() {
        watch(createObserver());
    }

    /**
     * Подготовка "отслеживателя" для файла.
     */
    private FileAlterationObserver createObserver() {
        String directory = pathToFile.getParent().toString();
        FileAlterationObserver observer = new FileAlterationObserver(directory, createFilter());
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onStart(FileAlterationObserver observer) {
                LOGGER.info("Starting to listen " + fileName + " file");
            }

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
     * Обработка события изменения файла.
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
     * Запуск отслеживания файла.
     */
    private void watch(FileAlterationObserver observer){
        FileAlterationMonitor monitor = new FileAlterationMonitor(fileWatchInterval, observer);

        try {
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
