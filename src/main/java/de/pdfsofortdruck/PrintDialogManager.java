package de.pdfsofortdruck;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Consumer;

/** Zeigt die Druckdialoge nacheinander an und schließt ggf. den Edge-Tab. */
public final class PrintDialogManager {

    private static final int FOCUS_DELAY_MS = 300;

    private record Pending(Path file, Object browserWindow) {
    }

    private final WatcherConfig config;
    private final EdgeTabCloser edgeTabCloser;
    private final Consumer<String> logger;
    private final Queue<Pending> queue = new ArrayDeque<>();
    private boolean dialogActive;

    public PrintDialogManager(WatcherConfig config, EdgeTabCloser edgeTabCloser, Consumer<String> logger) {
        this.config = config;
        this.edgeTabCloser = edgeTabCloser;
        this.logger = logger;
    }

    /** Wird aus dem Wächter-Thread aufgerufen. */
    public void enqueue(Path file) {
        // Fenster merken, solange der Browser noch im Vordergrund steht.
        Object browserWindow;
        try {
            browserWindow = WindowsForeground.captureForeground();
        } catch (Throwable e) {
            browserWindow = null; // Ohne JNA einfach Strg+W an das aktive Fenster senden.
        }
        synchronized (this) {
            queue.add(new Pending(file, browserWindow));
            if (dialogActive) {
                return;
            }
            dialogActive = true;
        }
        SwingUtilities.invokeLater(this::showNext);
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
                SwingUtilities.invokeLater(this::showNext);
            }, "edge-tab-close");
            closer.setDaemon(true);
            closer.start();
        } else {
            showNext();
        }
    }
}
