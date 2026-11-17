package de.pdfsofortdruck;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Einstiegspunkt: Konfiguration laden, Wächter starten und Oberfläche anzeigen. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> start(args));
    }

    private static void start(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Path configFile = configPath(args);
            WatcherConfig config = WatcherConfig.load(configFile);

            WatcherFrame frame = new WatcherFrame(config, configFile);
            PrintDialogManager dialogs = new PrintDialogManager(config);
            DownloadWatcher watcher = new DownloadWatcher(config, dialogs::enqueue, frame::log);
            frame.setWatcher(watcher);
            frame.setVisible(true);
            frame.setState(java.awt.Frame.ICONIFIED); // Statusfenster sofort minimieren
            watcher.start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "PDF Sofortdruck konnte nicht gestartet werden:\n" + e.getMessage(),
                    "Startfehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** config.json aus Argument 1, aus dem Arbeitsverzeichnis oder neben der Jar suchen. */
    private static Path configPath(String[] args) {
        if (args.length > 0) {
            return Paths.get(args[0]).toAbsolutePath();
        }
        Path workingConfig = Paths.get("").toAbsolutePath().resolve("config.json");
        if (Files.exists(workingConfig)) {
            return workingConfig;
        }
        Path codeSource = codeSource();
        Path appDir = codeSource != null
                ? (Files.isDirectory(codeSource) ? codeSource : codeSource.getParent())
                : Paths.get("").toAbsolutePath();
        Path appConfig = appDir.resolve("config.json");
        if (Files.exists(appConfig)) {
            return appConfig;
        }
        boolean runningFromJar = codeSource != null
                && codeSource.getFileName() != null
                && codeSource.getFileName().toString().endsWith(".jar");
        return runningFromJar ? appConfig : workingConfig;
    }

    private static Path codeSource() {
        try {
            return Paths.get(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }
}
