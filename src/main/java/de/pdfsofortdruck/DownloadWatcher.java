package de.pdfsofortdruck;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Überwacht das Downloadverzeichnis und meldet neue, fertig geschriebene PDF-Dateien. */
public final class DownloadWatcher implements AutoCloseable {

    private record Candidate(long size, long lastModified, long firstSeen) {
    }

    private final WatcherConfig config;
    private final Consumer<Path> onFile;
    private final Consumer<String> logger;
    private final Map<Path, Candidate> candidates = new HashMap<>();
    private final Set<Path> handled = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread thread;

    public DownloadWatcher(WatcherConfig config, Consumer<Path> onFile, Consumer<String> logger) {
        this.config = config;
        this.onFile = onFile;
        this.logger = logger;
    }

    /** Startet die Überwachung. Bereits vorhandene Dateien werden optional ignoriert. */
    public void start() throws IOException {
        if (!Files.isDirectory(config.downloadDirectory())) {
            throw new IOException("Downloadverzeichnis existiert nicht: " + config.downloadDirectory());
        }
        if (!config.processExistingOnStart()) {
            handled.addAll(listMatchingFiles());
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::run, "download-watcher");
        thread.setDaemon(true);
        thread.start();
        logger.accept("Wächter läuft: " + config.downloadDirectory());
    }

    private void run() {
        while (running.get()) {
            try {
                scan();
            } catch (Exception e) {
                logger.accept("Fehler beim Prüfen des Verzeichnisses: " + e.getMessage());
            }
            try {
                Thread.sleep(config.pollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void scan() throws IOException {
        Set<Path> present = listMatchingFiles();
        long now = System.currentTimeMillis();

        for (Path file : present) {
            if (handled.contains(file)) {
                continue;
            }
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException e) {
                continue; // Datei wird gerade verschoben oder gelöscht.
            }

            Candidate previous = candidates.get(file);
            if (previous == null
                    || previous.size() != attributes.size()
                    || previous.lastModified() != attributes.lastModifiedTime().toMillis()) {
                candidates.put(file, new Candidate(
                        attributes.size(), attributes.lastModifiedTime().toMillis(), now));
                continue;
            }

            // Erst melden, wenn die Datei über mindestens stabilityMs unverändert blieb.
            if (attributes.size() > 0 && now - previous.firstSeen() >= config.stabilityMs()) {
                candidates.remove(file);
                handled.add(file);
                logger.accept("Neue PDF erkannt: " + file.getFileName());
                SwingUtilities.invokeLater(() -> onFile.accept(file));
            }
        }

        candidates.keySet().retainAll(present);
        handled.retainAll(present);
    }

    private Set<Path> listMatchingFiles() throws IOException {
        Set<Path> result = new HashSet<>();
        try (Stream<Path> files = Files.list(config.downloadDirectory())) {
            files.filter(Files::isRegularFile)
                    .filter(path -> config.fileNamePattern().matcher(path.getFileName().toString()).matches())
                    .forEach(result::add);
        }
        return result;
    }

    @Override
    public void close() {
        running.set(false);
        if (thread != null) {
            thread.interrupt();
        }
    }
}
