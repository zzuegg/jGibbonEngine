package dev.engine.core.asset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Watches a directory for file modifications and dispatches callbacks.
 * Uses the JDK {@link WatchService} on a daemon thread.
 */
public class FileWatcher {

    private static final Logger log = LoggerFactory.getLogger(FileWatcher.class);

    private final Path directory;
    private final Map<String, List<Runnable>> listeners = new ConcurrentHashMap<>();
    private volatile WatchService watchService;
    private volatile Thread watchThread;
    private volatile boolean running;

    public FileWatcher(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    /**
     * Starts watching the directory for MODIFY events on a daemon thread.
     */
    public void start() {
        if (running) return;
        try {
            watchService = directory.getFileSystem().newWatchService();
            directory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start file watcher on " + directory, e);
        }

        running = true;
        watchThread = new Thread(this::watchLoop, "FileWatcher-" + directory.getFileName());
        watchThread.setDaemon(true);
        watchThread.start();
        log.debug("FileWatcher started on {}", directory);
    }

    /**
     * Registers a callback for when the given file path is modified.
     * The path should be relative to the watched directory, or an absolute path.
     */
    public void addListener(String path, Runnable callback) {
        // Normalize to relative path string
        String key = normalizeKey(path);
        listeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(callback);
    }

    /**
     * Stops the file watcher and its daemon thread.
     */
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("Error closing watch service", e);
            }
        }
        if (watchThread != null) {
            watchThread.interrupt();
        }
        log.debug("FileWatcher stopped");
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                String changedFile = pathEvent.context().toString();

                var callbacks = listeners.get(changedFile);
                if (callbacks != null) {
                    for (Runnable cb : callbacks) {
                        try {
                            cb.run();
                        } catch (Exception e) {
                            log.error("Error in file watcher callback for {}", changedFile, e);
                        }
                    }
                }
            }

            boolean valid = key.reset();
            if (!valid) {
                log.warn("Watch key no longer valid for {}", directory);
                break;
            }
        }
    }

    private String normalizeKey(String path) {
        Path p = Path.of(path);
        if (p.isAbsolute()) {
            p = directory.relativize(p.toAbsolutePath().normalize());
        }
        return p.normalize().toString();
    }
}
