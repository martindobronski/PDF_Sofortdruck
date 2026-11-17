package de.pdfsofortdruck;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Schließt nach dem Druck den aktiven Edge-Tab per Strg+W – aber nur, wenn im
 * Vordergrund tatsächlich msedge.exe läuft.
 */
public final class EdgeTabCloser {

    private static final String EDGE_PROCESS = "msedge";

    private static final String FOREGROUND_SCRIPT = """
            Add-Type @"
            using System;
            using System.Runtime.InteropServices;
            public static class ForegroundWindow {
                [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
                [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
            }
            "@
            $handle = [ForegroundWindow]::GetForegroundWindow()
            $processId = [uint32]0
            [ForegroundWindow]::GetWindowThreadProcessId($handle, [ref]$processId) | Out-Null
            (Get-Process -Id $processId).ProcessName
            """;

    private final Consumer<String> logger;

    public EdgeTabCloser(Consumer<String> logger) {
        this.logger = logger;
    }

    public void closeIfEdgeIsForeground() {
        if (!isWindows()) {
            logger.accept("Edge-Tab schließen ist nur unter Windows verfügbar – übersprungen.");
            return;
        }
        try {
            String process = foregroundProcessName();
            if (process == null || !EDGE_PROCESS.equalsIgnoreCase(process)) {
                logger.accept("Edge-Tab nicht geschlossen – Vordergrund: "
                        + (process == null ? "unbekannt" : process));
                return;
            }
            sendCtrlW();
            logger.accept("Edge-Tab nach dem Druck geschlossen.");
        } catch (Exception e) {
            logger.accept("Edge-Tab konnte nicht geschlossen werden: " + e.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String foregroundProcessName() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", FOREGROUND_SCRIPT);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.joining("\n"));
        }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Zeitüberschreitung bei der Fensterprüfung");
        }
        if (output.isEmpty()) {
            return null;
        }
        String[] lines = output.split("\\R");
        return lines[lines.length - 1].trim();
    }

    private void sendCtrlW() throws AWTException {
        Robot robot = new Robot();
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.delay(40);
        robot.keyPress(KeyEvent.VK_W);
        robot.delay(80);
        robot.keyRelease(KeyEvent.VK_W);
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }
}
