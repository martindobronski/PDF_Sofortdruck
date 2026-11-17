package de.pdfsofortdruck;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

/** Zeigt die Druckdialoge nacheinander an und schließt ggf. den Edge-Tab. */
public final class PrintDialogManager {

    private static final int FOCUS_DELAY_MS = 300;

    private final WatcherConfig config;
    private final EdgeTabCloser edgeTabCloser;
    private final Queue<Path> queue = new ArrayDeque<>();
    private boolean dialogActive;

    public PrintDialogManager(WatcherConfig config, EdgeTabCloser edgeTabCloser) {
        this.config = config;
        this.edgeTabCloser = edgeTabCloser;
    }

    /** Wird aus dem Wächter-Thread aufgerufen. */
    public void enqueue(Path file) {
        synchronized (this) {
            queue.add(file);
            if (dialogActive) {
                return;
            }
            dialogActive = true;
        }
        SwingUtilities.invokeLater(this::showNext);
    }

    private void showNext() {
        Path file;
        synchronized (this) {
            file = queue.poll();
            if (file == null) {
                dialogActive = false;
                return;
            }
        }

        PrintDialog dialog = new PrintDialog(file, config);
        dialog.setVisible(true); // Modaler Dialog; kehrt nach dispose() zurück.

        if (config.closeEdgeTabAfterPrint() && dialog.isPrintSucceeded()) {
            Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(FOCUS_DELAY_MS); // Edge wieder Fokus geben lassen.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                edgeTabCloser.closeActiveTab();
                SwingUtilities.invokeLater(this::showNext);
            }, "edge-tab-close");
            closer.setDaemon(true);
            closer.start();
        } else {
            showNext();
        }
    }
}
