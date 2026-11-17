# PDF Sofortdruck

Ausführliche Betriebs- und Nachschlageanleitung: [ANLEITUNG.md](ANLEITUNG.md)

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
3. Starten mit `javaw -jar pdf-sofortdruck.jar` (oder per Doppelklick, falls `.jar` mit Java verknüpft ist).

**Wichtig bei einem Start per Batch-Datei:** Bitte `javaw.exe` statt `java.exe` verwenden.
`java.exe` öffnet ein Konsolenfenster; nach dem Schließen des Druckdialogs kann dieses
den Fokus erhalten, sodass `Strg+W` an die Konsole geht statt an den Browser. `javaw.exe`
startet ohne Konsole und verhält sich wie der Start aus der IDE.

**Wichtig zum Arbeitsverzeichnis:** Ohne explizites Argument sucht das Programm die
`config.json` zuerst im aktuellen Arbeitsverzeichnis und erst danach neben der Jar.
Ein Batch mit anderem `cd` kann daher eine andere Konfiguration und eine andere
`pdf-sofortdruck.log` verwenden. Die Zeilen `Arbeitsverzeichnis:` und `Konfiguration:`
im Log zeigen genau, welche Datei geladen wurde. Am sichersten ist die Config als
Argument anzugeben (siehe unten).

**Wichtig für eine abgespeckte JRE/jlink-Runtime:** `java.awt.Robot` (für `Strg+W`) gehört
zum Modul `java.desktop` und benötigt dessen native `awt.dll` aus der Runtime. Eine
jlink-Runtime muss daher mindestens `java.desktop` enthalten. Beim Start schreibt das
Programm `Robot (Tastatureingabe) verfügbar.` bzw. eine Fehlermeldung in
`pdf-sofortdruck.log`. Im Zweifel die Jar mit einer vollständigen JRE starten.

Das Programm läuft als kleines Statusfenster, das direkt nach dem Start minimiert wird
und dauerhaft minimiert bleibt – es kann nicht in den Vordergrund kommen. Dort werden
auch die Version (`v0.02 vom 17.11.2026`), das überwachte Verzeichnis und die
Konfigurationsdatei angezeigt. Zusätzlich wird neben der `config.json` die Datei
`pdf-sofortdruck.log` mit allen Meldungen geschrieben – hilfreich, weil das Statusfenster
minimiert bleibt. Der Druckdialog erscheint unabhängig davon im Vordergrund und schließt
sich nach dem Druck automatisch. Zum Beenden das Statusfenster in der Windows-Taskleiste
schließen.
Eine alternative Konfigurationsdatei kann als Argument übergeben werden:

```bat
java -jar pdf-sofortdruck.jar "C:\Programme\PDF Sofortdruck\config.json"
```

## config.json

Beispiel:

```json
{
  "downloadDirectory": "C:/MD/Download",
  "fileNamePattern": "\\d{6}_\\d{18}\\.pdf",
  "pollIntervalMs": 1000,
  "stabilityMs": 800,
  "printerName": "",
  "processExistingOnStart": false,
  "deleteAfterPrint": false,
  "closeEdgeTabAfterPrint": true
}
```

| Einstellung | Bedeutung |
| --- | --- |
| `downloadDirectory` | Zu überwachendes Verzeichnis. Auf Windows funktionieren sowohl `C:/MD/Download` als auch `C:\\MD\\Download`. |
| `fileNamePattern` | Regulärer Ausdruck für den Basis-Dateinamen. Standard: 6 Ziffern, `_`, 18 Ziffern, `.pdf` (Groß-/Kleinschreibung egal). Ein von Browsern angehängter Zähler wird automatisch entfernt und muss nicht im Muster stehen. In JSON muss jeder Backslash verdoppelt werden. |
| `pollIntervalMs` | Prüfabstand in Millisekunden, mindestens 200. |
| `stabilityMs` | Eine Datei gilt erst nach dieser Zeit ohne Größen-/Zeitänderung als fertig geschrieben. Verhindert Dialoge während des Downloads. |
| `printerName` | Leer = Standarddrucker. Optional der exakte Name eines Druckers (Groß-/Kleinschreibung egal). |
| `processExistingOnStart` | `false` = schon vorhandene passende Dateien beim Start ignorieren. `true` = auch diese melden. |
| `deleteAfterPrint` | `true` = die PDF wird nach erfolgreichem Druck gelöscht. `false` = Datei bleibt liegen. Bei Abbruch oder Druckfehler wird nichts gelöscht. |
| `closeEdgeTabAfterPrint` | `true` = nach erfolgreichem Druck wird einfach `Strg+W` an das Vordergrundfenster gesendet. `false` = kein Tastendruck. Bei Abbruch oder Druckfehler wird nichts gesendet. |

Fehlt die `config.json`, wird sie beim ersten Start mit Standardwerten angelegt. Als
Downloadverzeichnis wird dann `Downloads` im Benutzerprofil eingetragen.

## Verhalten

- Der Standard-Ausdruck `\\d{6}_\\d{18}\\.pdf` passt auf `210607_148844746850961591.pdf`.
- Bei jedem weiteren Download hängt der Browser einen Zähler an. Sowohl
  `210607_148844746850961591(1).pdf` als auch `210607_148844746850961591 (1).pdf`
  (mit Leerzeichen) und höhere Zähler werden ebenfalls erkannt. Der Zähler wird vor dem
  Abgleich mit dem Muster entfernt, daher funktioniert das auch mit bereits vorhandenen Konfigurationen.
- Die Datei muss vollständig geschrieben sein, bevor der Dialog erscheint.
- `Enter` oder `Drucken` löst den Druck aus. Ein Doppelklick bzw. `Enter` plus Button
  erzeugt nur einen Auftrag.
- `Abbrechen` oder `Esc` überspringt die Datei; in diesem Fall wird sie nicht gelöscht.
- Mit `deleteAfterPrint: true` wird die Datei nur nach erfolgreichem Druck entfernt.
  Schlägt das Löschen fehl, erscheint eine Warnung; der Druckauftrag bleibt trotzdem erfolgt.
- Tauchen mehrere Dateien gleichzeitig auf, werden die Dialoge nacheinander angezeigt.
- Der Druckdialog wird immer im Vordergrund geöffnet und nach dem Druck geschlossen.
- Der Fokus wird beim Öffnen des Dialogs explizit gesetzt (nicht erst beim Schließen – bei
  einem modalen Dialog blockiert `setVisible(true)` bis `dispose()`). Falls der
  Browser-Download-Dialog ihn danach übernimmt, holt der Dialog ihn bis zu 8 Sekunden lang
  selbst zurück und beendet die Überwachung, sobald er eine Sekunde stabil im Vordergrund
  war (`Druckdialog ohne Fokus – ...` im Log). Beim Start wird außerdem einmal ein
  unsichtbarer modaler Dialog geöffnet und sofort geschlossen (`Modaler Dialog initialisiert.`
  im Log), damit AWTs geteilte Dialog-Infrastruktur schon steht, wenn die erste PDF kommt.
- Mit `closeEdgeTabAfterPrint: true` sendet das Programm ca. 300 ms nach dem Druck einfach
  `Strg+W` an das Vordergrundfenster – ohne Prozess- oder URL-Prüfung. Der Browser steht
  beim Auslösen des Drucks im Vordergrund und erhält nach dem Dialog wieder den Fokus.
  Wenn der PDF-Tab der letzte Tab war, schließt sich das gesamte Edge-Fenster.
- Unter macOS wird das Schließen des Edge-Tabs übersprungen (Windows-only).
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
├── EdgeTabCloser.java    Edge-Tab nach Druck per Strg+W schließen (Windows)
└── WatcherFrame.java     Statusfenster
```
