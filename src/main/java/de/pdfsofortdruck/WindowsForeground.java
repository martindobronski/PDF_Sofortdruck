package de.pdfsofortdruck;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.awt.Window;
import java.util.Locale;

/**
 * Windows-Fokus-Helfer: merkt sich das Vordergrundfenster vor dem Druckdialog,
 * erzwingt den Fokus für den Dialog und stellt das Browserfenster vor dem Senden
 * von Strg+W wieder her. Auf anderen Systemen sind alle Methoden No-ops.
 *
 * Die JNA-Typen bleiben bewusst innerhalb dieser Klasse. Nach außen wird ein
 * opakes Objekt als Fenster-Handle gereicht, damit ein fehlendes JNA weder den
 * Start noch das Senden von Strg+W verhindert.
 */
final class WindowsForeground {

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean ShowWindow(Pointer hWnd, int nCmdShow);

        boolean BringWindowToTop(Pointer hWnd);

        boolean SetForegroundWindow(Pointer hWnd);

        Pointer GetForegroundWindow();

        int GetWindowThreadProcessId(Pointer hWnd, IntByReference processId);

        boolean AttachThreadInput(int idAttach, int idAttachTo, boolean fAttach);

        boolean IsWindow(Pointer hWnd);

        Pointer GetAncestor(Pointer hWnd, int gaFlags);

        int GetWindowTextLengthW(Pointer hWnd);

        int GetWindowTextW(Pointer hWnd, char[] text, int maxCount);

        void keybd_event(byte virtualKey, byte scan, int flags, Pointer extraInfo);

        void SwitchToThisWindow(Pointer hWnd, boolean altTab);
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetCurrentThreadId();
    }

    private static final int SW_SHOW = 5;
    private static final int GA_ROOT = 2;
    private static final byte VK_MENU = 0x12;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    private WindowsForeground() {
    }

    static String diagnose() {
        if (!isWindows()) {
            return "Fokus-Erzwingung nur unter Windows – übersprungen.";
        }
        try {
            return "JNA/Windows-API verfügbar. Vordergrund beim Start: "
                    + describe(User32.INSTANCE.GetForegroundWindow());
        } catch (Throwable e) {
            return "JNA/Windows-API NICHT verfügbar: " + e;
        }
    }

    /** Merkt sich das Fenster, das vor dem Druckdialog im Vordergrund war (z. B. Edge). */
    static Object captureForeground() {
        if (!isWindows()) {
            return null;
        }
        try {
            Pointer hWnd = User32.INSTANCE.GetForegroundWindow();
            if (hWnd == null) {
                return null;
            }
            Pointer root = User32.INSTANCE.GetAncestor(hWnd, GA_ROOT);
            return root != null ? root : hWnd;
        } catch (Throwable e) {
            return null;
        }
    }

    static String describe(Object handle) {
        if (!(handle instanceof Pointer hWnd)) {
            return "kein Fenster";
        }
        return "'" + title(hWnd) + "' (" + handle(hWnd) + ")";
    }

    /** Prüft, ob das angegebene Fenster aktuell den Windows-Vordergrund hat. */
    static boolean isForeground(Window window) {
        if (!isWindows()) {
            return true;
        }
        try {
            Pointer hWnd = Native.getComponentPointer(window);
            return hWnd != null && hWnd.equals(User32.INSTANCE.GetForegroundWindow());
        } catch (Throwable e) {
            return false;
        }
    }

    /** Bringt den Druckdialog in den Vordergrund und liefert eine Diagnosezeile. */
    static String force(Window window) {
        if (!isWindows()) {
            return "Fokus-Erzwingung nur unter Windows – übersprungen.";
        }
        try {
            Pointer hWnd = Native.getComponentPointer(window);
            if (hWnd == null) {
                return "Druckdialog: kein Fenster-Handle.";
            }
            String before = describe(User32.INSTANCE.GetForegroundWindow());
            boolean ok = bringToFront(hWnd);
            return "Druckdialog-Fokus: vorher " + before + ", SetForegroundWindow=" + ok
                    + ", nachher " + describe(User32.INSTANCE.GetForegroundWindow());
        } catch (Throwable e) {
            return "Druckdialog-Fokus fehlgeschlagen: " + e;
        }
    }

    /** Stellt das gemerkte Browserfenster wieder her, damit Strg+W dort ankommt. */
    static String restore(Object handle) {
        if (!isWindows()) {
            return "";
        }
        if (handle == null) {
            return "Kein Browserfenster gemerkt.";
        }
        try {
            if (!(handle instanceof Pointer hWnd) || !User32.INSTANCE.IsWindow(hWnd)) {
                return "Kein gültiges Browserfenster gemerkt.";
            }
            String before = describe(User32.INSTANCE.GetForegroundWindow());
            boolean ok = bringToFront(hWnd);
            return "Browserfokus wiederhergestellt: vorher " + before + ", SetForegroundWindow=" + ok
                    + ", nachher " + describe(User32.INSTANCE.GetForegroundWindow());
        } catch (Throwable e) {
            return "Browserfenster konnte nicht wiederhergestellt werden: " + e;
        }
    }

    private static boolean bringToFront(Pointer hWnd) {
        User32 user = User32.INSTANCE;

        // Windows vergibt den Vordergrund nur an den Prozess mit der letzten Eingabe.
        // Ein synthetischer ALT-Druck hebt diese Sperre auf.
        try {
            user.keybd_event(VK_MENU, (byte) 0, 0, null);
            user.keybd_event(VK_MENU, (byte) 0, KEYEVENTF_KEYUP, null);
            // Windows muss die synthetische Eingabe erst verarbeiten, sonst wird
            // SetForegroundWindow beim allerersten Aufruf noch abgelehnt.
            Thread.sleep(40);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
            // Optional – der Rest wird trotzdem versucht.
        }

        IntByReference processId = new IntByReference();
        int foregroundThread = 0;
        int ownThread = 0;
        try {
            foregroundThread = user.GetWindowThreadProcessId(user.GetForegroundWindow(), processId);
            ownThread = Kernel32.INSTANCE.GetCurrentThreadId();
        } catch (Throwable ignored) {
            // Dann ohne AttachThreadInput weitermachen.
        }

        boolean attached = false;
        if (foregroundThread != 0 && ownThread != 0 && foregroundThread != ownThread) {
            try {
                attached = user.AttachThreadInput(ownThread, foregroundThread, true);
            } catch (Throwable ignored) {
                // Nicht kritisch.
            }
        }

        try {
            user.ShowWindow(hWnd, SW_SHOW);
            user.BringWindowToTop(hWnd);
            boolean ok = false;
            try {
                ok = user.SetForegroundWindow(hWnd);
            } catch (Throwable ignored) {
                // Fallback unten.
            }
            if (!ok) {
                try {
                    user.SwitchToThisWindow(hWnd, true);
                    ok = user.SetForegroundWindow(hWnd);
                } catch (Throwable ignored) {
                    // Letzter Versuch war erfolglos.
                }
            }
            return ok;
        } finally {
            if (attached) {
                try {
                    user.AttachThreadInput(ownThread, foregroundThread, false);
                } catch (Throwable ignored) {
                    // Aufräumen darf nicht fehlschlagen.
                }
            }
        }
    }

    private static String title(Pointer hWnd) {
        try {
            int length = User32.INSTANCE.GetWindowTextLengthW(hWnd);
            if (length <= 0) {
                return "<unbenannt>";
            }
            char[] buffer = new char[Math.min(length + 1, 512)];
            int count = User32.INSTANCE.GetWindowTextW(hWnd, buffer, buffer.length);
            return count > 0 ? new String(buffer, 0, count) : "<unbenannt>";
        } catch (Throwable e) {
            return "<unbekannt>";
        }
    }

    private static String handle(Pointer hWnd) {
        return String.format("0x%X", Pointer.nativeValue(hWnd));
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
