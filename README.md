# PDF Sofortdruck

Kleine Java/Swing-Oberfläche, die ein Downloadverzeichnis überwacht. Sobald eine Datei
mit dem Namensformat `210607_148844746850961591.pdf` – optional auch mit Zähler wie
`210607_148844746850961591(1).pdf` – auftaucht, erscheint ein Dialog.
`Drucken` oder die `Enter`-Taste sendet die PDF sofort an den eingestellten Standarddrucker –
ohne weiteren Druckdialog.

Entwicklung auf macOS, Einsatz auf Windows: Die Anwendung nutzt ausschließlich die
plattformunabhängige Java-Druckschnittstelle (`javax.print`) und rendert die PDF seitenweise
über Apache PDFBox. Auf Windows wird damit der in den Windows-Druckereinstellungen definierte
Standarddrucker verwendet.

## Build

Voraussetzung: JDK 17 oder neuer und Maven.

```bash
mvn package
```

Fertige, lauffähige Datei (inkl. PDFBox):

```text
target/pdf-sofortdruck.jar
```

## Einsatz auf dem Windows-Rechner

1. `target/pdf-sofortdruck.jar` und `config.json` in einen gemeinsamen Ordner kopieren.
2. `config.json` anpassen (siehe unten).
3. Starten mit `java -jar pdf-sofortdruck.jar` (oder per Doppelklick, falls `.jar` mit Java verknüpft ist).

Das Programm läuft als kleines Statusfenster, das direkt nach dem Start minimiert wird.
Dort werden auch die Version (`v0.01 vom 17.11.2026`), das überwachte Verzeichnis und die
 Konfigurationsdatei angezeigt. `Beenden` im Statusfenster beendet den Wächter. Der
 Druckdialog erscheint unabhängig davon im Vordergrund und schließt sich nach dem Druck
 automatisch.
Eine alternative Konfigurationsdatei kann als Argument übergeben werden:

```bat
java -jar pdf-sofortdruck.jar "C:\Programme\PDF Sofortdruck\config.json"
```

## config.json

Beispiel:

```json
{
  "downloadDirectory": "C:/Users/Meier/Downloads",
  "fileNamePattern": "\\d{6}_\\d{18}(?:\\(\\d+\\))?\\.pdf",
  "pollIntervalMs": 1000,
  "stabilityMs": 800,
  "printerName": "",
  "processExistingOnStart": false,
  "deleteAfterPrint": false
}
```

| Einstellung | Bedeutung |
| --- | --- |
| `downloadDirectory` | Zu überwachendes Verzeichnis. Auf Windows funktionieren sowohl `C:/Users/Meier/Downloads` als auch `C:\\Users\\Meier\\Downloads`. |
| `fileNamePattern` | Regulärer Ausdruck für den Dateinamen. Standard: 6 Ziffern, `_`, 18 Ziffern, optional `(Zähler)`, `.pdf` (Groß-/Kleinschreibung egal). In JSON muss jeder Backslash verdoppelt werden. |
| `pollIntervalMs` | Prüfabstand in Millisekunden, mindestens 200. |
| `stabilityMs` | Eine Datei gilt erst nach dieser Zeit ohne Größen-/Zeitänderung als fertig geschrieben. Verhindert Dialoge während des Downloads. |
| `printerName` | Leer = Standarddrucker. Optional der exakte Name eines Druckers (Groß-/Kleinschreibung egal). |
| `processExistingOnStart` | `false` = schon vorhandene passende Dateien beim Start ignorieren. `true` = auch diese melden. |
| `deleteAfterPrint` | `true` = die PDF wird nach erfolgreichem Druck gelöscht. `false` = Datei bleibt liegen. Bei Abbruch oder Druckfehler wird nichts gelöscht. |

Fehlt die `config.json`, wird sie beim ersten Start mit Standardwerten angelegt. Als
Downloadverzeichnis wird dann `Downloads` im Benutzerprofil eingetragen.

## Verhalten

- Der Standard-Ausdruck `\\d{6}_\\d{18}(?:\\(\\d+\\))?\\.pdf` passt auf
  `210607_148844746850961591.pdf`, `210607_148844746850961591(1).pdf`,
  `210607_148844746850961591(2).pdf` usw. Der Zähler vor `.pdf` ist optional.
- Die Datei muss vollständig geschrieben sein, bevor der Dialog erscheint.
- `Enter` oder `Drucken` löst den Druck aus. Ein Doppelklick bzw. `Enter` plus Button
  erzeugt nur einen Auftrag.
- `Abbrechen` oder `Esc` überspringt die Datei; in diesem Fall wird sie nicht gelöscht.
- Mit `deleteAfterPrint: true` wird die Datei nur nach erfolgreichem Druck entfernt.
  Schlägt das Löschen fehl, erscheint eine Warnung; der Druckauftrag bleibt trotzdem erfolgt.
- Tauchen mehrere Dateien gleichzeitig auf, werden die Dialoge nacheinander angezeigt.
- Der Druckdialog wird immer im Vordergrund geöffnet und nach dem Druck geschlossen.
- Ist kein Standarddrucker eingestellt oder der gewünschte Drucker nicht vorhanden,
  erscheint eine Fehlermeldung.

## Entwicklung / Test auf macOS

```bash
mvn package
java -jar target/pdf-sofortdruck.jar
```

Zum Testen kann `downloadDirectory` auf einen lokalen Testordner gesetzt werden. Das
Drucken funktioniert auf macOS über das dort eingestellte Standardziel (CUPS/PDF-Drucker)
genauso wie auf Windows über den Windows-Standarddrucker.

## Projektstruktur

```text
src/main/java/de/pdfsofortdruck/
├── Main.java              Start, Konfigurationspfad, Look-and-Feel
├── WatcherConfig.java     Laden/Anlegen der config.json
├── Json.java             Minimaler JSON-Reader für die flache Konfiguration
├── DownloadWatcher.java  Überwachung und Erkennung fertiger Dateien
├── PrintDialogManager.java  Warteschlange für mehrere Dialoge
├── PrintDialog.java      Dialog mit Drucken/Enter
├── PdfPrinter.java       Drucken auf den Standarddrucker
└── WatcherFrame.java     Statusfenster
```
