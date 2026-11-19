package de.pdfsofortdruck;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Verhindert, dass zwei Instanzen der Anwendung gleichzeitig laufen.
 *
 * <p>Der Schutz basiert auf einem exklusiven {@link FileLock} auf einer festen Datei im
 * Temp-Verzeichnis. Das Betriebssystem gibt den Lock beim Beenden des Prozesses automatisch
 * frei – auch bei einem Absturz. Eine reine „Datei existiert"-Prüfung würde dagegen nach
 * einem Absturz eine verwaiste Lockdatei hinterlassen.</p>
 */
public final class SingleInstanceLock {

    /** Ergebnis der Lockprüfung. */
    public enum Result {
        /** Lock erhalten – die Anwendung darf starten. */
        ACQUIRED,
        /** Eine andere Instanz hält den Lock – diese Instanz darf nicht starten. */
        ALREADY_RUNNING,
        /** Der Lock konnte nicht geprüft werden (z. B. kein Schreibzugriff auf das Temp-Verzeichnis). */
        ERROR
    }

    private static FileChannel channel;
    private static FileLock lock;
    private static String lastError;

    private SingleInstanceLock() {
    }

    /** Pfad der Lockdatei: {@code java.io.tmpdir/pdf-sofortdruck.lock}. */
    public static Path lockFile() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "pdf-sofortdruck.lock");
    }

    /**
     * Versucht, den exklusiven Lock zu erhalten. Channel und Lock werden in statischen
     * Feldern gehalten – sonst würden sie vom Garbage Collector freigegeben.
     */
    public static Result acquire() {
        lastError = null;
        try {
            Path file = lockFile();
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                closeChannel();
                return Result.ALREADY_RUNNING;
            }
            return Result.ACQUIRED;
        } catch (IOException | RuntimeException e) {
            lastError = e.toString();
            closeChannel();
            return Result.ERROR;
        }
    }

    /** Gibt den Lock aktiv frei (zusätzlich zum automatischen Freigeben beim Prozessende). */
    public static void release() {
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException ignored) {
                // Beim Prozessende gibt das OS den Lock ohnehin frei.
            }
            lock = null;
        }
        closeChannel();
    }

    /** Fehlerursache, falls {@link #acquire()} {@link Result#ERROR} geliefert hat. */
    public static String lastError() {
        return lastError;
    }

    private static void closeChannel() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ignored) {
                // Nichts zu tun.
            }
            channel = null;
        }
    }
}
