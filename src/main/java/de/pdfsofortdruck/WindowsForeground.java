package de.pdfsofortdruck;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

import java.awt.Window;
import java.util.Locale;

/** Erzwingt den Windows-Fokus für ein AWT-Fenster. Auf anderen Systemen ein No-op. */
final class WindowsForeground {

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean ShowWindow(Pointer hWnd, int nCmdShow);

        boolean BringWindowToTop(Pointer hWnd);

        boolean SetForegroundWindow(Pointer hWnd);

        boolean SetFocus(Pointer hWnd);

        Pointer GetForegroundWindow();

        int GetWindowThreadProcessId(Pointer hWnd, IntByReference processId);

        boolean AttachThreadInput(int idAttach, int idAttachTo, boolean fAttach);
    }

    private interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetCurrentThreadId();
    }

    private static final int SW_SHOW = 5;

    private WindowsForeground() {
    }

    static String diagnose() {
        if (!isWindows()) {
            return "Fokus-Erzwingung nur unter Windows – übersprungen.";
        }
        try {
            User32.INSTANCE.GetForegroundWindow();
            return "JNA/Windows-API verfügbar.";
        } catch (Throwable e) {
            return "JNA/Windows-API NICHT verfügbar: " + e;
        }
    }

    static void force(Window window) {
        if (!isWindows()) {
            return;
        }
        try {
            Pointer hWnd = Native.getComponentPointer(window);
            if (hWnd == null) {
                return;
            }
            User32 user = User32.INSTANCE;
            IntByReference processId = new IntByReference();
            int foregroundThread = user.GetWindowThreadProcessId(user.GetForegroundWindow(), processId);
            int ownThread = Kernel32.INSTANCE.GetCurrentThreadId();
            boolean attached = false;
            if (foregroundThread != 0 && foregroundThread != ownThread) {
                attached = user.AttachThreadInput(ownThread, foregroundThread, true);
            }
            try {
                user.ShowWindow(hWnd, SW_SHOW);
                user.BringWindowToTop(hWnd);
                user.SetForegroundWindow(hWnd);
                user.SetFocus(hWnd);
            } finally {
                if (attached) {
                    user.AttachThreadInput(ownThread, foregroundThread, false);
                }
            }
        } catch (Throwable ignored) {
            // Falls die API nicht verfügbar ist, bleibt es beim normalen Swing-Fokus.
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
