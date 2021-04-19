package dev.wilix.ldap.facade.file;

import dev.wilix.ldap.facade.file.config.properties.FileStorageConfigurationProperties;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
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

public class FileWatcher {
    private final static Logger LOGGER = LoggerFactory.getLogger(FileWatcher.class);
    private final Path pathToFile;
    private final int interval;
    private final Consumer<String> fileContentListener;
    private final String fileName;

    public FileWatcher(FileStorageConfigurationProperties config, Consumer<String> fileContentListener) {
        this.pathToFile = config.getPathToFile();
        this.interval = config.getInterval();
        this.fileContentListener = fileContentListener;
        this.fileName = pathToFile.getFileName().toString();
    }

    public void watchFileChanges() {
        IOFileFilter filter = createFilter();
        FileAlterationObserver observer = createObserver(filter);
        watch(observer);
    }

    private IOFileFilter createFilter() {
        IOFileFilter directories = FileFilterUtils.and(
                FileFilterUtils.directoryFileFilter(),
                HiddenFileFilter.VISIBLE);

        IOFileFilter files = FileFilterUtils.and(
                FileFilterUtils.fileFileFilter(),
                FileFilterUtils.suffixFileFilter(fileName));

        return FileFilterUtils.or(directories, files);
    }

    private FileAlterationObserver createObserver(IOFileFilter filter) {
        File fileWatched = pathToFile.toFile();
        if (!fileWatched.exists()) {
            throw new IllegalStateException("File at the specified path does not exist.");
        }

        String directory = fileWatched.getParent();
        FileAlterationObserver observer = new FileAlterationObserver(directory, filter);
        observer.addListener(new FileAlterationListenerAdaptor() {
            @Override
            public void onStart(FileAlterationObserver observer) {
                LOGGER.info("Starting to listen " + fileName + " file");
            }

            @Override
            public void onFileChange(File file) {
                LOGGER.info("Receive file " + fileName + " change event.");
                processChangeEvent();
            }

            private void processChangeEvent() {
                String fileContent = null;
                try {
                    fileContent = Files.readString(pathToFile);
                } catch (IOException e) {
                    throw new IllegalStateException("Can't read watched wile!", e);
                }
                fileContentListener.accept(fileContent);
            }
        });
        return observer;
    }

    private void watch(FileAlterationObserver observer){
        FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);

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
