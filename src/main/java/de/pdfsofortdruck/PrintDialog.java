package de.pdfsofortdruck;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/** Dialog, der beim Auftauchen einer passenden PDF angezeigt wird. */
public final class PrintDialog extends JDialog {

    private final Path file;
    private final WatcherConfig config;
    private final JLabel statusLabel;
    private final JButton printButton = new JButton("Drucken");
    private final JButton closeButton = new JButton("Abbrechen");
    private boolean printStarted;
    private boolean printSucceeded;

    public PrintDialog(Path file, WatcherConfig config) {
        // Kein Owner: der Dialog bleibt sichtbar, obwohl das Statusfenster minimiert ist.
        super((Frame) null, "PDF sofort drucken", true);
        this.file = file;
        this.config = config;
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

        setMinimumSize(new Dimension(460, 180));
        setResizable(false);
        setAlwaysOnTop(true);
        pack();
        setLocationRelativeTo(null);
    }

    public boolean isPrintSucceeded() {
        return printSucceeded;
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            // Der Dialog hat keinen Owner; Fokus daher explizit nach dem Anzeigen setzen.
            EventQueue.invokeLater(() -> {
                toFront();
                requestFocus();
                printButton.requestFocus();
            });
        }
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
