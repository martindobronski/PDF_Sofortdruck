# PDF Sofortdruck – Betriebs- und Nachschlageanleitung

Diese Anleitung beschreibt, wofür das Programm da ist, wie die ausführbare Jar arbeitet
und was bei der Einrichtung auf dem Windows-Rechner zu beachten ist. Sie ist als
Nachschlagewerk gedacht – auch wenn die Entwicklung schon eine Weile her ist.

Stand: **v0.02 vom 17.11.2026** (Maven-Version `1.1.0`)

---

## 1. Zweck

Beim Herunterladen bestimmter PDFs aus dem Browser soll der Ausdruck **ohne zusätzliche
Klicks** passieren. Das Programm überwacht dazu ein Downloadverzeichnis. Sobald eine
passende, vollständig geschriebene PDF auftaucht, erscheint ein kleiner Dialog. `Enter`
oder die Schaltfläche `Drucken` schickt die Datei direkt an den Drucker – ohne
Druckereinstellungen-Dialog. Danach wird optional der zugehörige Browser-Tab geschlossen.

Typischer Anwendungsfall: PDFs mit Namen wie

```text
210607_148844746850961591.pdf
210607_148844746850961591 (1).pdf
210607_148844746850961591(2).pdf
```

Entwicklung erfolgte auf macOS, Einsatz ist Windows. Es werden nur plattformunabhängige
Java-APIs (`javax.print`) plus Apache PDFBox verwendet; die Windows-spezifischen Teile
sind Fokus-Erzwingung und das Senden von `Strg+W`.

---

## 2. Wie die Jar arbeitet

Ablauf vom Download bis zum geschlossenen Tab:

1. **Start:** `Main` prüft zuerst per Datei-Lock, ob bereits eine Instanz läuft (siehe 6.7).
   Ist das nicht der Fall, lädt er die `config.json`, zeigt optional den Splash (siehe 6.8),
   erstellt das dauerhaft minimierte Statusfenster und initialisiert einmalig einen
   unsichtbaren modalen Dialog (siehe Abschnitt 6).
2. **Wächter:** `DownloadWatcher` prüft alle `pollIntervalMs` Millisekunden das
   `downloadDirectory`. Eine Datei zählt erst als fertig, wenn Größe und Änderungszeit
   über `stabilityMs` Millisekunden konstant blieben.
3. **Namenserkennung:** Browser-Zähler wie ` (1)` oder `(2)` werden vor dem Abgleich
   entfernt. Übrig bleibt der Basisname, der auf `fileNamePattern` geprüft wird.
4. **Dialog:** `PrintDialogManager` zeigt einen modalen Dialog im Vordergrund. Fokus wird
   über die Windows-API (JNA) aktiv erzwungen und für maximal 8 Sekunden überwacht. Sobald
   der Vordergrund bestätigt ist, wird einmalig eine `Tab`-Taste gesendet – der Dialog kann
   nämlich im Vordergrund liegen, ohne dass ein Button den Tastaturfokus hat. Erst dadurch
   löst `Enter` zuverlässig `Drucken` aus.
5. **Druck:** `PdfPrinter` rendert die PDF seitenweise über PDFBox und ruft
   `PrinterJob.print()` auf – bewusst **ohne** `printDialog()`. Bei leerem `printerName`
   wird der Windows-Standarddrucker benutzt, sonst der angegebene Drucker.
6. **Nach dem Druck:** Der Dialog schließt sich automatisch. Bei Erfolg und aktivierter
   Option wird nach ca. 300 ms `Strg+W` gesendet, um den Browser-Tab zu schließen. Bei
   `deleteAfterPrint: true` wird die Datei gelöscht.
7. **Warteschlange:** Mehrere gefundene Dateien werden nacheinander abgearbeitet.

**Wichtig:** `Strg+W` geht an das gerade fokussierte Fenster. Es gibt bewusst keine Prüfung,
ob dort wirklich Edge läuft. Wenn zwischenzeitlich ein anderes Programm den Fokus hat,
kann dort ein Tab/Fenster geschlossen werden.

---

## 3. Lieferumfang

| Datei | Zweck |
| --- | --- |
| `target/pdf-sofortdruck.jar` | Lauffähige Jar, **ca. 5,4 MB**. Enthält PDFBox **und** JNA samt `jnidispatch.dll`. |
| `Start.bat` | Start-datei; eine Verknüpfung davon kann auf den Desktop gelegt werden. |
| `config.json` | Konfiguration. Wird beim ersten Start automatisch angelegt, falls sie fehlt. |
| `pdf-sofortdruck.log` | Wird neben der geladenen `config.json` geschrieben – die wichtigste Diagnosequelle. |

Die Jar ist ein **Fat Jar** (Maven Shade Plugin). Es müssen keine weiteren Bibliotheken
nebenher kopiert werden. Die Lockdatei gegen Mehrfachstart (`pdf-sofortdruck.lock`) liegt
nicht im Programmordner, sondern im Windows-Temp-Verzeichnis (`%TEMP%`). Sie muss nicht
gelöscht werden.

---

## 4. Einrichtung auf Windows (Schritt für Schritt)

1. Ordner anlegen, z. B. `C:\Programme\PDF Sofortdruck`.
2. `pdf-sofortdruck.jar` und `config.json` hineinkopieren.
3. `config.json` anpassen – mindestens `downloadDirectory` und ggf.
   `closeEdgeTabAfterPrint` (siehe Abschnitt 5).
4. Start mit der mitgelieferten `Start.bat` – am besten per Verknüpfung auf dem Desktop
   (siehe Abschnitt 7).
5. Kurz prüfen, ob `pdf-sofortdruck.log` im selben Ordner erscheint und dort
   `Wächter läuft: ...` steht.
6. Test-Download in den überwachten Ordner legen und `Enter` drücken.

Das Statusfenster wird sofort minimiert und bleibt dauerhaft minimiert. Zum Beenden die
Taskleiste benutzen (Rechtsklick → Schließen) oder im Statusfenster auf `Beenden` klicken,
falls es kurz sichtbar war.

---

## 5. config.json

```json
{
  "downloadDirectory": "C:/MD/Download",
  "fileNamePattern": "\\d{6}_.*\\.pdf",
  "pollIntervalMs": 1000,
  "stabilityMs": 800,
  "printerName": "",
  "processExistingOnStart": false,
  "deleteAfterPrint": false,
  "closeEdgeTabAfterPrint": true,
  "showSplash": true,
  "splashDurationMs": 3000,
  "setMediaAttributesFromPdf": false,
  "printDpi": 0
}
```

| Einstellung | Bedeutung |
| --- | --- |
| `downloadDirectory` | Überwachtes Verzeichnis. `C:/MD/Download` und `C:\\MD\\Download` funktionieren beide. |
| `fileNamePattern` | Regulärer Ausdruck für den Basisnamen. Standard: 6 Ziffern, `_`, beliebige weitere Zeichen, `.pdf`. Browser-Zähler werden automatisch entfernt. In JSON jeden Backslash verdoppeln. |
| `pollIntervalMs` | Prüfabstand in ms, mindestens 200. |
| `stabilityMs` | Datei muss diese Zeit lang unverändert sein, bevor sie gemeldet wird. Verhindert zu frühe Dialoge während des Downloads. |
| `printerName` | Leer = Windows-Standarddrucker. Sonst exakter Druckername (Groß-/Kleinschreibung egal). |
| `processExistingOnStart` | `false` = bereits vorhandene Dateien werden ignoriert. `true` = auch diese werden gemeldet. |
| `deleteAfterPrint` | `true` = PDF wird nach erfolgreichem Druck gelöscht. Bei Abbruch/Fehler bleibt sie liegen. |
| `closeEdgeTabAfterPrint` | `true` = nach erfolgreichem Druck `Strg+W` senden. `false` = kein Tastendruck. **Diese Option war bereits einmal die Ursache für „Tab schließt nicht" – zuerst prüfen!** |
| `showSplash` | `true` = Startbildschirm bei **jedem** Programmstart anzeigen. `false` = gar kein Splash. Standard: `true`. |
| `splashDurationMs` | Dauer des Splashs in ms, mindestens 0. Standard: `3000`. Der Splash verschwindet früher, wenn ein Druckdialog erscheint. Bei `showSplash: false` wirkungslos. |
| `setMediaAttributesFromPdf` | **Testoption (Schritt 1):** `true` = Papierformat und Ausrichtung werden aus der PDF (erste Seite) ermittelt und als Druckattribute angefordert. Ziel: treiberabhängige Format-Defaults und damit leichte Versätze/Randunterschiede reduzieren. Standard: `false` = bisheriges Verhalten. |
| `printDpi` | **Testoption (Schritt 2):** `0` = bisheriges Standard-Rendering. Wert > 0 (z. B. `300`) = feste Render-DPI für das PDF-Rendering. Ziel: konsistenteres Ergebnis auf Druckern mit unterschiedlicher Auflösung. |

Fehlt die Datei, wird sie mit Standardwerten neu angelegt (Downloadverzeichnis = `Downloads`
im Benutzerprofil, `closeEdgeTabAfterPrint: false`, `showSplash: true`,
`splashDurationMs: 3000`, `setMediaAttributesFromPdf: false`, `printDpi: 0`).

**Rollback zum bisherigen Druckverhalten:** `setMediaAttributesFromPdf` auf `false` und
`printDpi` auf `0` setzen (oder die beiden Zeilen weglassen) – dann verhält sich der Druck
wieder wie in der aktuellen Version. Die gewählten Optionen werden beim Druck ins Log
geschrieben (`Druck: PDFPageable ...`, `Druck: Medienattribute ...`).

---

## 6. Fallstricke und warum es so gelöst ist

Diese Punkte sind im Laufe der Entwicklung aufgefallen. Sie sind die häufigsten
Fehlerquellen und sollten bei Problemen zuerst geprüft werden.

### 6.1 `javaw.exe` statt `java.exe`

`java.exe` öffnet ein Konsolenfenster. Nach dem Schließen des Druckdialogs kann dieses den
Fokus bekommen, sodass `Strg+W` an die Konsole geht statt an den Browser. `javaw.exe`
startet ohne Konsole – daher immer dies verwenden.

### 6.2 Arbeitsverzeichnis und Konfigurationspfad

`Main.configPath()` sucht in dieser Reihenfolge:

1. Pfad aus `args[0]`, falls übergeben
2. `config.json` im aktuellen Arbeitsverzeichnis
3. `config.json` neben der Jar

Ein Batch mit anderem `cd` kann also eine andere Config und eine andere Logdatei verwenden.
Am sichersten: Config als Argument übergeben (siehe Abschnitt 7). Das Log schreibt beim
Start `Arbeitsverzeichnis:` und `Konfiguration:` – daran sieht man sofort, was geladen wurde.

### 6.3 Jar-Version – JNA muss enthalten sein

Die aktuelle Jar ist **ca. 5,4 MB** und enthält `com/sun/jna/Native.class` sowie
`com/sun/jna/win32-x86-64/jnidispatch.dll`. Ältere Versionen (ca. 3,6 MB) haben kein JNA.
Wird `NoClassDefFoundError: com/sun/jna/Native` geloggt, läuft eine alte Jar. In IntelliJ
musste dazu das Artefakt „runnable jar" entfernt und neu hinzugefügt werden, damit die
neue Jar übernommen wird.

Seit der letzten Version ist JNA so gekapselt, dass ein Fehlen den Start und das
`Strg+W` nicht mehr bricht – der Fokus kann dann aber nicht erzwungen werden.

### 6.4 Abgespeckte jlink-Runtime

Für `java.awt.Robot` (Tastendruck) und JNA muss die Runtime mindestens enthalten:

- `java.desktop` – Swing, AWT, Robot
- `jdk.unsupported` – `sun.misc.Unsafe`, wird von JNA benötigt
- `java.logging`

JNA entpackt `jnidispatch.dll` zur Laufzeit nach `java.io.tmpdir`. Wenn dieses
Verzeichnis nicht existiert oder nicht beschreibbar ist, gibt es einen
`UnsatisfiedLinkError`. Im Zweifel eine vollständige JRE verwenden.

### 6.5 Fokus des Druckdialogs

Der erste Dialog eines Prozesses hatte früher keinen Fokus. Ursachen und Gegenmaßnahmen:

- Bei einem **modalen** Dialog blockiert `setVisible(true)` bis `dispose()`. Fokus-Code
  darf daher nicht danach stehen, sondern läuft in `windowOpened()`.
- AWT legt beim ersten modalen Dialog einen geteilten Owner-Rahmen an. Deshalb wird beim
  Start einmal ein unsichtbarer 1×1-Dialog geöffnet und sofort geschlossen.
- Windows erlaubt `SetForegroundWindow` nur dem Prozess mit der letzten Eingabe. Ein
  synthetischer ALT-Tastendruck plus `AttachThreadInput` hebt diese Sperre auf.
- Der Dialog prüft 8 Sekunden lang alle 200 ms selbst, ob er noch im Vordergrund ist.

Diese Maßnahmen greifen nur, wenn JNA vorhanden ist.

### 6.6 Tab schließen

`Strg+W` wird ca. 300 ms nach erfolgreichem Druck gesendet. Vorher wird das Browserfenster,
das bei Erkennung der PDF im Vordergrund war, wieder aktiviert. Bei Batch-Start mit
`java.exe` ging der Tastendruck an die Konsole – siehe 6.1.

### 6.7 Mehrfachstart-Schutz

Es kann immer nur eine Instanz laufen. `SingleInstanceLock` hält einen exklusiven
`FileLock` auf `%TEMP%\pdf-sofortdruck.lock` (bzw. `java.io.tmpdir/pdf-sofortdruck.lock`).
Ein zweiter Start erkennt den gehaltenen Lock, zeigt „PDF Sofortdruck läuft bereits." und
beendet sich, **ohne** Wächter oder Splash zu starten.

Der Lock wird beim Beenden aktiv freigegeben und zusätzlich vom Betriebssystem – auch nach
einem Absturz. Deshalb ist nichts zu löschen. Ein verwaistes „Läuft bereits" trotz fehlendem
Fenster bedeutet meist, dass die Anwendung minimiert in der Taskleiste läuft.

Falls das Temp-Verzeichnis nicht beschreibbar ist, startet die Anwendung trotzdem; der
Fehler steht dann als `Mehrfachstart-Schutz nicht verfügbar: ...` im Log. Der Schutz ist in
diesem Fall allerdings wirkungslos.

### 6.8 Splashscreen

Der Splash ist eine eigene `JWindow`-Klasse und **kein** `java.awt.SplashScreen` – nur so
lässt er sich per `config.json` ein- und ausschalten und in der Dauer steuern. Er ist
randlos, zentriert und `alwaysOnTop`, aber bewusst **nicht fokussierbar**
(`setFocusableWindowState(false)`, `setAutoRequestFocus(false)`). Sonst könnte er – genau
wie ein Konsolenfenster – den Fokus für den Druckdialog oder `Strg+W` stören.

Er zeigt den Titel „PDF Sofortdruck", den Hinweis auf den startenden Wächter und die
Version `v0.02 vom 17.11.2026`. Ein `javax.swing.Timer` schließt ihn nach
`splashDurationMs`; `PrintDialogManager` schließt ihn zusätzlich sofort, wenn die erste PDF
kommt, damit sich keine zwei `alwaysOnTop`-Fenster überlagern.

---

## 7. Start per Batch-Datei

Die mitgelieferte `Start.bat` enthält bereits:

```bat
@echo off
cd /d "%~dp0"
start "" javaw -jar "%~dp0pdf-sofortdruck.jar" "%~dp0config.json"
```

`%~dp0` ist das Verzeichnis der Batchdatei. Damit sind Arbeitsverzeichnis, Jar und Config
unabhängig davon korrekt, von wo aus die Batch aufgerufen wird. `javaw` verhindert das
Konsolenfenster (siehe 6.1).

---

## 8. Log-Datei und Diagnose

`pdf-sofortdruck.log` liegt neben der geladenen `config.json`. Wichtige Zeilen:

| Log-Zeile | Bedeutung |
| --- | --- |
| `Arbeitsverzeichnis: ...` / `Konfiguration: ...` | Welcher Pfad wirklich benutzt wird. |
| `Geladene Jar: ...` / `Classpath: ...` | Welche Jar und welcher Classpath aktiv sind. |
| `Lockdatei: ...` | Pfad der Lockdatei für den Mehrfachstart-Schutz. |
| `Splash: an (3000 ms)` / `Splash: aus` | Ob der Startbildschirm aktiv ist und wie lange. |
| `Mehrfachstart-Schutz nicht verfügbar: ...` | Temp-Verzeichnis nicht beschreibbar – Schutz wirkungslos. |
| `Druck: PDFPageable ...` / `Druck: Medienattribute ...` | Aktive Druckoptionen (Standard-DPI/Medien aus PDF) und erkannte Seitengröße – wichtig für Drucktests. |
| `JNA-Klasse im Classpath vorhanden.` / `FEHLT ...` | Ob JNA gefunden wurde. |
| `JNA/Windows-API verfügbar. Vordergrund beim Start: ...` | Windows-API erreichbar, inkl. Titel des Vordergrundfensters. |
| `Robot (Tastatureingabe) verfügbar.` | `java.awt.Robot` nutzbar. |
| `Wächter läuft: ...` | Überwachung gestartet. |
| `Neue PDF erkannt: ...` | Datei wurde als fertig erkannt. |
| `Druckdialog geöffnet – Fokus wird gesetzt. AWT-Fokusfenster=...` | Dialog ist erschienen; zeigt, wen AWT als fokussiert ansieht. |
| `Druckdialog-Fokus: vorher ... nachher ...` | Ergebnis der Fokus-Erzwingung. |
| `Tab-Taste gesendet – Fokus auf dem Druckbutton.` | Einmaliger Tab nach Fokusbestätigung, damit `Enter` den Druckbutton auslöst. |
| `Druckdialog ohne Fokus – Fokus wird erneut gesetzt.` | Browser hat den Fokus übernommen; Watchdog greift ein. |
| `Browserfokus wiederhergestellt: ...` | Vor `Strg+W` wurde das Browserfenster aktiviert. |
| `Strg+W nach dem Druck gesendet.` | Tab-Schließ-Versuch wurde ausgeführt. |

### Häufige Symptome

| Symptom | Erste Prüfung |
| --- | --- |
| Tab schließt sich nicht | `closeEdgeTabAfterPrint` in der **geladenen** Config prüfen; `javaw` verwenden. |
| `NoClassDefFoundError: com/sun/jna/Native` | Alte Jar (3,6 MB) im Einsatz → aktuelle 5,4-MB-Jar übernehmen, Artefakt in IntelliJ neu hinzufügen. |
| `Enter` löst nicht aus | Prüfen, ob `Tab-Taste gesendet ...` im Log steht. Der `Abbrechen`-Button ist bewusst nicht fokussierbar, damit `Enter` immer `Drucken` auslöst. |
| Fokus nur ab dem zweiten Download | JNA fehlt oder Jar alt; siehe 6.3/6.5. |
| Kein Druck | Standarddrucker eingestellt? `printerName` exakt? Log auf `Druck fehlgeschlagen` prüfen. |
| Datei wird nicht erkannt | Namensformat prüfen; `fileNamePattern` in JSON korrekt escaped? |
| Meldung „läuft bereits", obwohl kein Fenster sichtbar | Statusfenster ist dauerhaft minimiert → in der Taskleiste prüfen. Falls wirklich nichts läuft, `%TEMP%\pdf-sofortdruck.lock` prüfen (siehe 6.7). |
| Splash erscheint nicht | `showSplash` in der **geladenen** Config prüfen; bei sehr kurzem `splashDurationMs` ist er kaum sichtbar. |
| Splash bleibt zu lange stehen | Er schließt sich sofort beim ersten Druckdialog; ansonsten `splashDurationMs` prüfen. |

---

## 9. Entwicklung / Build

Voraussetzungen: JDK 17 oder neuer, Maven.

```bash
mvn clean package
```

Ergebnis: `target/pdf-sofortdruck.jar`. Auf macOS kann mit einem lokalen Testordner
entwickelt werden; das Drucken geht dort über CUPS. Das Tab-Schließen ist Windows-only und
wird auf anderen Systemen übersprungen.

### Projektstruktur

```text
src/main/java/de/pdfsofortdruck/
├── Main.java                 Start, Mehrfachstart-Schutz, Konfigurationspfad, Diagnose
├── SingleInstanceLock.java   Datei-Lock gegen Mehrfachstart
├── SplashWindow.java         Konfigurierbarer Startbildschirm
├── WatcherConfig.java        Laden/Anlegen der config.json
├── Json.java                 Minimaler JSON-Reader für die flache Config
├── DownloadWatcher.java      Verzeichnisüberwachung, Zählererkennung
├── PrintDialogManager.java   Warteschlange und Tab-Schließen
├── PrintDialog.java          Dialog, Enter/Button, Fokus-Watchdog
├── PdfPrinter.java           Direktdruck über javax.print + PDFBox
├── EdgeTabCloser.java        Strg+W senden (Windows)
├── WindowsForeground.java    Fokus über Windows-API (JNA)
└── WatcherFrame.java         Dauerhaft minimiertes Statusfenster + Logdatei
```

### Quellen und Versionierung

- Remote: `https://github.com/martindobronski/PDF_Sofortdruck.git`, Branch `master`
- Tag `first-version` zeigt auf die erste veröffentlichte Fassung (`79de208`)
- Version im Hauptdialog: `v0.02 vom 17.11.2026`
