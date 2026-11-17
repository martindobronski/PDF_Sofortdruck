package de.pdfsofortdruck;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Schließt nach dem Druck den aktiven Tab per Strg+W. Vorher wird das Browserfenster,
 * das beim Erkennen der PDF im Vordergrund war, wieder aktiviert – sonst geht der
 * Tastendruck je nach Startart (Batch mit Konsole!) an das falsche Fenster.
 */
public final class EdgeTabCloser {

    private final Consumer<String> logger;

    public EdgeTabCloser(Consumer<String> logger) {
        this.logger = logger;
    }

    public void closeActiveTab(Object browserWindow) {
        if (!isWindows()) {
            logger.accept("Strg+W wird nur unter Windows gesendet – übersprungen.");
            return;
        }
        String focusInfo;
        try {
            focusInfo = WindowsForeground.restore(browserWindow);
        } catch (Throwable e) {
            focusInfo = "Browserfokus konnte nicht gesetzt werden: " + e;
        }
        try {
            sendCtrlW();
            logger.accept("Strg+W nach dem Druck gesendet. " + focusInfo);
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
