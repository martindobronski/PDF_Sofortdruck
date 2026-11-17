package de.pdfsofortdruck;

import com.sun.jna.Pointer;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Zeigt die Druckdialoge nacheinander an und schließt ggf. den Edge-Tab. */
public final class PrintDialogManager {

    private static final int FOCUS_DELAY_MS = 300;

    private record Pending(Path file, Pointer browserWindow) {
    }

    private final WatcherConfig config;
    private final EdgeTabCloser edgeTabCloser;
    private final Consumer<String> logger;
    private final Queue<Pending> queue = new ArrayDeque<>();
    private final ScheduledExecutorService delayExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dialog-delay");
        thread.setDaemon(true);
        return thread;
    });
    private boolean dialogActive;

    public PrintDialogManager(WatcherConfig config, EdgeTabCloser edgeTabCloser, Consumer<String> logger) {
        this.config = config;
        this.edgeTabCloser = edgeTabCloser;
        this.logger = logger;
    }

    /** Wird aus dem Wächter-Thread aufgerufen. */
    public void enqueue(Path file) {
        // Fenster merken, solange der Browser noch im Vordergrund steht.
        Pointer browserWindow = WindowsForeground.captureForeground();
        synchronized (this) {
            queue.add(new Pending(file, browserWindow));
            if (dialogActive) {
                return;
            }
            dialogActive = true;
        }
        scheduleShow();
    }

    /** Erst anzeigen, wenn der Browser seinen Download-Dialog aufgebaut hat. */
    private void scheduleShow() {
        delayExecutor.schedule(() -> SwingUtilities.invokeLater(this::showNext),
                config.dialogDelayMs(), TimeUnit.MILLISECONDS);
    }

    private void showNext() {
        Pending pending;
        synchronized (this) {
            pending = queue.poll();
            if (pending == null) {
                dialogActive = false;
                return;
            }
        }

        PrintDialog dialog = new PrintDialog(pending.file(), config, logger);
        dialog.setVisible(true); // Modaler Dialog; kehrt nach dispose() zurück.

        if (config.closeEdgeTabAfterPrint() && dialog.isPrintSucceeded()) {
            Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(FOCUS_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                edgeTabCloser.closeActiveTab(pending.browserWindow());
                scheduleShow();
            }, "edge-tab-close");
            closer.setDaemon(true);
            closer.start();
        } else {
            showNext();
        }
    }
}
