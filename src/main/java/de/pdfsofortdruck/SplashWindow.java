package de.pdfsofortdruck;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * Kleiner, randloser Startbildschirm.
 *
 * <p>Der Splash ist bewusst <em>nicht</em> fokussierbar: das Statusfenster bleibt dauerhaft
 * minimiert und der Splash darf weder den Fokus des Druckdialogs noch das Senden von
 * {@code Strg+W} stören.</p>
 */
public final class SplashWindow {

    /** Gemeinsame Versionsangabe für Splash und Statusfenster. */
    static final String VERSION = "v0.02 vom 17.11.2026";

    private final JWindow window;
    private Timer timer;

    public SplashWindow() {
        window = new JWindow();
        window.setFocusableWindowState(false);
        window.setAutoRequestFocus(false);
        window.setAlwaysOnTop(true);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 90, 130), 2),
                BorderFactory.createEmptyBorder(28, 48, 24, 48)));

        JLabel title = new JLabel("PDF Sofortdruck", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JLabel info = new JLabel("Downloadwächter wird gestartet …", SwingConstants.CENTER);
        info.setFont(info.getFont().deriveFont(Font.PLAIN, 12f));

        JLabel version = new JLabel(VERSION, SwingConstants.CENTER);
        version.setFont(version.getFont().deriveFont(Font.PLAIN, 11f));
        version.setForeground(new Color(90, 90, 90));

        content.add(title, BorderLayout.NORTH);
        content.add(info, BorderLayout.CENTER);
        content.add(version, BorderLayout.SOUTH);

        window.setContentPane(content);
        window.pack();
        window.setLocationRelativeTo(null);
    }

    /** Zeigt den Splash und schließt ihn nach {@code durationMs} automatisch. */
    public void show(long durationMs) {
        window.setVisible(true);
        int delay = (int) Math.min(Math.max(durationMs, 1), Integer.MAX_VALUE);
        timer = new Timer(delay, event -> close());
        timer.setRepeats(false);
        timer.start();
    }

    /** Schließt den Splash sofort (z. B. wenn ein Druckdialog erscheint). */
    public void close() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        window.setVisible(false);
        window.dispose();
    }
}
