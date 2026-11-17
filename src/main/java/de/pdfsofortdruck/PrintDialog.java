package de.pdfsofortdruck;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Timer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Dialog, der beim Auftauchen einer passenden PDF angezeigt wird. */
public final class PrintDialog extends JDialog {

    /**
     * Initialisiert beim Programmstart AWTs geteilten Owner-Rahmen für modale Dialoge.
     * Der Dummy-Dialog liegt außerhalb des Bildschirms und nimmt selbst keinen Fokus.
     * Danach verhält sich der erste echte Druckdialog wie jeder weitere.
     */
    static void warmUp() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(PrintDialog::warmUp);
            return;
        }
        JDialog dummy = new JDialog((Frame) null, "", true);
        dummy.setUndecorated(true);
        dummy.setSize(1, 1);
        dummy.setLocation(-2000, -2000);
        dummy.setAutoRequestFocus(false);
        EventQueue.invokeLater(dummy::dispose);
        dummy.setVisible(true);
    }

    private static final int FOCUS_CHECK_MS = 200;
    private static final int FOCUS_WATCHDOG_MS = 8000;
    private static final int FOCUS_STABLE_CHECKS = 5; // 1 s stabil im Vordergrund

    private final Path file;
    private final WatcherConfig config;
    private final Consumer<String> logger;
    private final JLabel statusLabel;
    private final JButton printButton = new JButton("Drucken");
    private final JButton closeButton = new JButton("Abbrechen");
    private boolean printStarted;
    private boolean printSucceeded;
    private Timer focusTimer;
    private long focusWatchdogEnd;
    private int focusConfirmedChecks;

    public PrintDialog(Path file, WatcherConfig config, Consumer<String> logger) {
        // Kein Owner: der Dialog bleibt sichtbar, obwohl das Statusfenster minimiert ist.
        super((Frame) null, "PDF sofort drucken", true);
        this.file = file;
        this.config = config;
        this.logger = logger;
        closeButton.setFocusable(false); // Enter darf niemals "Abbrechen" auslösen.

        JLabel headline = new JLabel("Neue Druckdatei gefunden");
        headline.setFont(headline.getFont().deriveFont(Font.BOLD, 15f));
        headline.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));

        JLabel fileName = new JLabel(file.getFileName().toString());
        fileName.setBorder(BorderFactory.createEmptyBorder(0, 16, 4, 16));

        statusLabel = new JLabel("Drucken mit Enter oder über die Schaltfläche.");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 16));
        buttons.add(printButton);
        buttons.add(closeButton);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setBorder(BorderFactory.createEmptyBorder(0, 16, 4, 16));
        middle.add(fileName, BorderLayout.NORTH);
        middle.add(statusLabel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.add(headline, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);

        printButton.addActionListener(event -> startPrint());
        closeButton.addActionListener(event -> dispose());

        getRootPane().setDefaultButton(printButton);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "drucken");
        getRootPane().getActionMap().put("drucken", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                startPrint();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "schliessen");
        getRootPane().getActionMap().put("schliessen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                dispose();
            }
        });

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent event) {
                printButton.requestFocusInWindow();
            }
        });

        // WICHTIG: setVisible(true) blockiert bei einem modalen Dialog bis dispose().
        // Der Fokus muss daher in windowOpened gesetzt werden - nicht nach setVisible().
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                logger.accept("Druckdialog geöffnet – Fokus wird gesetzt. " + awtFocusInfo());
                EventQueue.invokeLater(() -> {
                    try {
                        logger.accept(WindowsForeground.force(PrintDialog.this));
                    } catch (Throwable e) {
                        logger.accept("Druckdialog-Fokus fehlgeschlagen: " + e);
                    }
                    applyFocus();
                });
                startFocusWatchdog();
            }
        });

        setMinimumSize(new Dimension(460, 180));
        setResizable(false);
        setAlwaysOnTop(true);
        pack();
        setLocationRelativeTo(null);
    }

    public boolean isPrintSucceeded() {
        return printSucceeded;
    }

    private void applyFocus() {
        toFront();
        requestFocus();
        printButton.requestFocus();
    }

    private String awtFocusInfo() {
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        Window focusedWindow = manager.getFocusedWindow();
        Component focusOwner = manager.getFocusOwner();
        return "AWT-Fokusfenster=" + (focusedWindow == null ? "null" : focusedWindow.getClass().getSimpleName())
                + ", Fokusinhaber=" + (focusOwner == null ? "null" : focusOwner.getClass().getSimpleName());
    }

    /**
     * Der Browser baut seinen Download-Dialog manchmal erst auf, nachdem unser Dialog
     * erschienen ist, und nimmt ihm dabei den Fokus. Deshalb prüfen wir kurzzeitig, ob
     * der Dialog noch im Vordergrund ist, und holen den Fokus bei Bedarf zurück.
     */
    private void startFocusWatchdog() {
        try {
            if (!WindowsForeground.isWindows()) {
                return;
            }
        } catch (Throwable e) {
            return; // Ohne JNA bleibt es beim normalen Swing-Fokus.
        }
        focusWatchdogEnd = System.currentTimeMillis() + FOCUS_WATCHDOG_MS;
        focusConfirmedChecks = 0;
        focusTimer = new Timer(FOCUS_CHECK_MS, event -> {
            try {
                if (!isDisplayable() || !isVisible() || System.currentTimeMillis() >= focusWatchdogEnd) {
                    logger.accept("Fokusüberwachung beendet.");
                    stopFocusWatchdog();
                    return;
                }
                boolean foreground;
                try {
                    foreground = WindowsForeground.isForeground(this);
                } catch (Throwable e) {
                    foreground = false;
                }
                if (foreground) {
                    if (++focusConfirmedChecks >= FOCUS_STABLE_CHECKS) {
                        logger.accept("Druckdialog hat stabil den Fokus – Überwachung beendet.");
                        stopFocusWatchdog();
                    }
                    return;
                }
                focusConfirmedChecks = 0;
                logger.accept("Druckdialog ohne Fokus – Fokus wird erneut gesetzt. " + awtFocusInfo());
                try {
                    logger.accept(WindowsForeground.force(this));
                } catch (Throwable e) {
                    logger.accept("Druckdialog-Fokus fehlgeschlagen: " + e);
                }
                applyFocus();
            } catch (Throwable e) {
                logger.accept("Fokusüberwachung fehlgeschlagen: " + e);
                stopFocusWatchdog();
            }
        });
        focusTimer.setRepeats(true);
        focusTimer.start();
    }

    private void stopFocusWatchdog() {
        if (focusTimer != null) {
            focusTimer.stop();
            focusTimer = null;
        }
    }

    @Override
    public void dispose() {
        stopFocusWatchdog();
        super.dispose();
    }

    private String afterPrintMessage() {
        if (!config.deleteAfterPrint()) {
            return "Gedruckt: " + file.getFileName();
        }
        try {
            Files.deleteIfExists(file);
            return "Gedruckt und gelöscht: " + file.getFileName();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Die Datei wurde gedruckt, konnte aber nicht gelöscht werden:\n" + e.getMessage(),
                    "Löschen fehlgeschlagen", JOptionPane.WARNING_MESSAGE);
            return "Gedruckt, Löschen fehlgeschlagen: " + file.getFileName();
        }
    }

    private void startPrint() {
        if (printStarted) {
            return; // Doppelklick oder Enter plus Button darf nur einmal drucken.
        }
        printStarted = true;
        printButton.setEnabled(false);
        statusLabel.setText("Druckauftrag wird gesendet …");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                PdfPrinter.print(file, config.printerName());
                return null;
            }

            @Override
            protected void done() {
                closeButton.setText("Schließen");
                try {
                    get();
                    printSucceeded = true;
                    afterPrintMessage();
                } catch (Exception e) {
                    Throwable cause = e instanceof ExecutionException ? e.getCause() : e;
                    statusLabel.setText("Druck fehlgeschlagen.");
                    JOptionPane.showMessageDialog(PrintDialog.this,
                            "Die Datei konnte nicht gedruckt werden:\n" + cause.getMessage(),
                            "Druckfehler", JOptionPane.ERROR_MESSAGE);
                } finally {
                    dispose(); // Dialog nach dem Druckauftrag schließen.
                }
            }
        }.execute();
    }
}
