package de.pdfsofortdruck;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Kleines Statusfenster des dauerhaft laufenden Wächters. */
public final class WatcherFrame extends JFrame {

    private static final String VERSION = "v0.02 vom 17.11.2026";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextArea logArea = new JTextArea(10, 50);
    private final Path logFile;
    private DownloadWatcher watcher;

    public WatcherFrame(WatcherConfig config, Path configFile) {
        super("PDF Sofortdruck – Wächter");
        Path parent = configFile.toAbsolutePath().getParent();
        logFile = (parent != null ? parent : Path.of("")).resolve("pdf-sofortdruck.log");

        JLabel state = new JLabel("Wächter läuft. Neue PDFs werden automatisch gemeldet.");
        state.setFont(state.getFont().deriveFont(Font.BOLD, 13f));

        JLabel directory = new JLabel("Downloadverzeichnis: " + config.downloadDirectory());
        JLabel settings = new JLabel("Konfiguration: " + configFile);
        settings.setFont(settings.getFont().deriveFont(Font.PLAIN, 11f));

        JLabel version = new JLabel(VERSION);
        version.setFont(version.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(settings, BorderLayout.WEST);
        footer.add(version, BorderLayout.EAST);

        JPanel info = new JPanel(new BorderLayout());
        info.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        info.add(state, BorderLayout.NORTH);
        info.add(directory, BorderLayout.CENTER);
        info.add(footer, BorderLayout.SOUTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createEmptyBorder(0, 14, 8, 14));

        JButton exitButton = new JButton("Beenden");
        exitButton.addActionListener(event -> exit());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 14, 12, 14));
        buttons.add(exitButton);

        setLayout(new BorderLayout());
        add(info, BorderLayout.NORTH);
        add(logScroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                exit();
            }
        });

        // Das Statusfenster darf niemals in den Vordergrund kommen.
        setFocusableWindowState(false);
        setAutoRequestFocus(false);
        addWindowStateListener(event -> {
            if ((event.getNewState() & Frame.ICONIFIED) == 0) {
                setState(Frame.ICONIFIED);
            }
        });

        setMinimumSize(new Dimension(560, 280));
        setLocationRelativeTo(null);
        pack();
    }

    public void setWatcher(DownloadWatcher watcher) {
        this.watcher = watcher;
    }

    /** Kann auch aus dem Wächter-Thread aufgerufen werden. */
    public void log(String message) {
        String line = LocalTime.now().format(TIME) + "  " + message;
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging darf den Wächter nicht beeinträchtigen.
        }
        SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void exit() {
        if (watcher != null) {
            watcher.close();
        }
        dispose();
        System.exit(0);
    }
}
