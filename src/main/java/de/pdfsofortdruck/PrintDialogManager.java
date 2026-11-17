package de.pdfsofortdruck;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

/** Zeigt die Druckdialoge nacheinander an, auch wenn mehrere PDFs gleichzeitig auftauchen. */
public final class PrintDialogManager {

    private final Frame owner;
    private final WatcherConfig config;
    private final Queue<Path> queue = new ArrayDeque<>();
    private boolean dialogActive;

    public PrintDialogManager(Frame owner, WatcherConfig config) {
        this.owner = owner;
        this.config = config;
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
        while (true) {
            Path file;
            synchronized (this) {
                file = queue.poll();
                if (file == null) {
                    dialogActive = false;
                    return;
                }
            }
            new PrintDialog(owner, file, config).setVisible(true);
        }
    }
}
