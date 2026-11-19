package de.pdfsofortdruck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Pattern;

/** Einstellungen aus der config.json. */
public record WatcherConfig(
        Path downloadDirectory,
        Pattern fileNamePattern,
        long pollIntervalMs,
        long stabilityMs,
        String printerName,
        boolean processExistingOnStart,
        boolean deleteAfterPrint,
        boolean closeEdgeTabAfterPrint) {

    private static final String DEFAULT_PATTERN = "\\d{6}_.*\\.pdf";

    /** Lädt die Konfiguration und legt sie beim ersten Start mit Standardwerten an. */
    public static WatcherConfig load(Path configFile) throws IOException {
        if (!Files.exists(configFile)) {
            String defaultDownload = Paths.get(System.getProperty("user.home"), "Downloads").toString();
            String json = """
                    {
                      "downloadDirectory": "%s",
                      "fileNamePattern": "\\\\d{6}_.*\\\\.pdf",
                      "pollIntervalMs": 1000,
                      "stabilityMs": 800,
                      "printerName": "",
                      "processExistingOnStart": false,
                      "deleteAfterPrint": false,
                      "closeEdgeTabAfterPrint": false
                    }
                    """.formatted(escapeJson(defaultDownload));
            Path parent = configFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(configFile, json, StandardCharsets.UTF_8);
        }

        Map<String, Object> values = Json.parseObject(Files.readString(configFile, StandardCharsets.UTF_8));

        String directory = string(values, "downloadDirectory",
                Paths.get(System.getProperty("user.home"), "Downloads").toString());
        String pattern = string(values, "fileNamePattern", DEFAULT_PATTERN);
        long poll = number(values, "pollIntervalMs", 1000);
        long stability = number(values, "stabilityMs", 800);
        String printer = string(values, "printerName", "");
        boolean existing = bool(values, "processExistingOnStart", false);
        boolean deleteAfterPrint = bool(values, "deleteAfterPrint", false);
        boolean closeEdgeTabAfterPrint = bool(values, "closeEdgeTabAfterPrint", false);

        if (directory.isBlank()) {
            throw new IOException("config.json: downloadDirectory darf nicht leer sein");
        }
        if (poll < 200) {
            throw new IOException("config.json: pollIntervalMs muss mindestens 200 sein");
        }
        if (stability < 200) {
            throw new IOException("config.json: stabilityMs muss mindestens 200 sein");
        }

        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (RuntimeException e) {
            throw new IOException("config.json: ungültiger fileNamePattern: " + e.getMessage(), e);
        }

        return new WatcherConfig(Paths.get(directory), compiled, poll, stability, printer.trim(), existing,
                deleteAfterPrint, closeEdgeTabAfterPrint);
    }

    private static String string(Map<String, Object> values, String key, String fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            throw new IOException("config.json: " + key + " muss eine Zeichenkette sein");
        }
        return text;
    }

    private static long number(Map<String, Object> values, String key, long fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new IOException("config.json: " + key + " muss eine Zahl sein");
        }
        return number.longValue();
    }

    private static boolean bool(Map<String, Object> values, String key, boolean fallback) throws IOException {
        Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IOException("config.json: " + key + " muss true oder false sein");
        }
        return flag;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
