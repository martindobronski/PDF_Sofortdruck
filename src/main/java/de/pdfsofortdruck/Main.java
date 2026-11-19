package de.pdfsofortdruck;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Robot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Einstiegspunkt: Konfiguration laden, Wächter starten und Oberfläche anzeigen. */
public final class Main {

    private static volatile String startupWarning;

    private Main() {
    }

    public static void main(String[] args) {
        SingleInstanceLock.Result lock = SingleInstanceLock.acquire();
        if (lock == SingleInstanceLock.Result.ALREADY_RUNNING) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "PDF Sofortdruck läuft bereits.\nEs kann immer nur eine Instanz gleichzeitig laufen.",
                    "PDF Sofortdruck", JOptionPane.INFORMATION_MESSAGE));
            return;
        }
        if (lock == SingleInstanceLock.Result.ERROR) {
            startupWarning = "Mehrfachstart-Schutz nicht verfügbar: " + SingleInstanceLock.lastError();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceLock::release, "single-instance-release"));
        SwingUtilities.invokeLater(() -> start(args));
    }

    private static void start(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            Path configFile = configPath(args);
            WatcherConfig config = WatcherConfig.load(configFile);

            SplashWindow splash = config.showSplash() ? new SplashWindow() : null;
            if (splash != null) {
                splash.show(config.splashDurationMs());
            }
            Runnable closeSplash = splash == null ? () -> { } : splash::close;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    closeSplash.run();
                } catch (Throwable ignored) {
                    // Beim JVM-Ende wird das Fenster ohnehin geschlossen.
                }
            }, "splash-close"));

            WatcherFrame frame = new WatcherFrame(config, configFile);
            PrintDialogManager dialogs =
                    new PrintDialogManager(config, new EdgeTabCloser(frame::log), frame::log, closeSplash);
            DownloadWatcher watcher = new DownloadWatcher(config, dialogs::enqueue, frame::log);
            frame.setWatcher(watcher);
            frame.log("Arbeitsverzeichnis: " + Paths.get("").toAbsolutePath());
            frame.log("Konfiguration: " + configFile.toAbsolutePath());
            frame.log("closeEdgeTabAfterPrint=" + config.closeEdgeTabAfterPrint());
            frame.log("Splash: " + (config.showSplash()
                    ? "an (" + config.splashDurationMs() + " ms)" : "aus"));
            frame.log("Lockdatei: " + SingleInstanceLock.lockFile());
            if (startupWarning != null) {
                frame.log(startupWarning);
            }
            frame.log("Java " + System.getProperty("java.version")
                    + ", Runtime: " + System.getProperty("java.home"));
            frame.log("Geladene Jar: " + codeSource());
            frame.log("Classpath: " + System.getProperty("java.class.path"));
            frame.log("Temp-Verzeichnis: " + System.getProperty("java.io.tmpdir"));
            try {
                Class.forName("com.sun.jna.Native", false, Main.class.getClassLoader());
                frame.log("JNA-Klasse im Classpath vorhanden.");
            } catch (Throwable e) {
                frame.log("JNA-Klasse FEHLT im Classpath: " + e);
            }
            try {
                new Robot();
                frame.log("Robot (Tastatureingabe) verfügbar.");
            } catch (Throwable e) {
                frame.log("Robot (Tastatureingabe) NICHT verfügbar: " + e);
            }
            try {
                frame.log(WindowsForeground.diagnose());
            } catch (Throwable e) {
                frame.log("JNA/Windows-API NICHT verfügbar: " + e);
            }
            frame.setVisible(true);
            frame.setState(java.awt.Frame.ICONIFIED); // Statusfenster sofort minimieren
            PrintDialog.warmUp(); // ersten modalen Dialog einmalig initialisieren
            frame.log("Modaler Dialog initialisiert.");
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
