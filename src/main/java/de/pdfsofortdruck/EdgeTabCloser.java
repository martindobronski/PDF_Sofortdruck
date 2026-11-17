package de.pdfsofortdruck;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Schließt nach dem Druck den aktiven Tab per Strg+W.
 * Es wird bewusst keine Prozess- oder URL-Prüfung gemacht – der Browser steht beim
 * Auslösen des Drucks im Vordergrund und bekommt den Fokus nach dem Dialog wieder.
 */
public final class EdgeTabCloser {

    private final Consumer<String> logger;

    public EdgeTabCloser(Consumer<String> logger) {
        this.logger = logger;
    }

    public void closeActiveTab() {
        if (!isWindows()) {
            logger.accept("Strg+W wird nur unter Windows gesendet – übersprungen.");
            return;
        }
        try {
            sendCtrlW();
            logger.accept("Strg+W nach dem Druck gesendet.");
        } catch (AWTException e) {
            logger.accept("Strg+W konnte nicht gesendet werden: " + e.getMessage());
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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
