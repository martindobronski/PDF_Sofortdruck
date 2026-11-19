# MABS - PDF Sofortdruck Erweiterung

Einfache Benutzeranleitung · **Version v0.02 vom 17.11.2026**

## Wofür ist das Programm?

Sie laden PDFs aus dem Browser herunter und möchten sie sofort ausdrucken, ohne jedes Mal
ein Druckfenster bedienen zu müssen.

Das Programm überwacht Ihren Download-Ordner. Sobald eine PDF vollständig heruntergeladen
ist, erscheint ein kleines Fenster. Drücken Sie die **Enter**-Taste oder klicken Sie auf
**Drucken**. Die PDF wird sofort ausgedruckt. Danach schließt sich das Fenster wieder, und
der Browser-Tab mit der PDF kann automatisch geschlossen werden.

Das Programm läuft im Hintergrund. Sie müssen sich um nichts weiter kümmern.

Diese Druckererweiterung wurde speziell für die Anwendung **MABS** entwickelt und
verarbeitet ausschließlich die MABS-Dokumentdateien.

---

## Installation

1. Kopieren Sie den **kompletten Programmordner `PDF_Sofortdruck`** zum Beispiel nach
   `y:\PDF_Sofortdruck`.
2. Erstellen Sie eine Verknüpfung zur Datei `Start.bat`:
   Rechtsklick auf `Start.bat` → **Senden an** → **Desktop (Verknüpfung erstellen)**.
3. Falls Sie möchten, benennen Sie die Verknüpfung auf dem Desktop in
   „PDF Sofortdruck" um.

**Hinweis bei Installation auf dem Netzlaufwerk:** Alle, die die Verknüpfung starten,
benutzen dieselben Einstellungen aus der Datei `config.json` im Programmordner.

---

## Einstellungen

Die Einstellungen stehen in der Datei `config.json` im Programmordner. Sie können die Datei
mit dem Windows-Editor (Notepad) öffnen und ändern. Speichern Sie die Datei und starten Sie
das Programm danach neu.

### Welcher Drucker benutzt wird

Suchen Sie die Zeile `"printerName"`:

```json
"printerName": ""
```

Wenn zwischen den Anführungszeichen nichts steht, wird auf den **Windows-Standarddrucker**
gedruckt – also auf den Drucker, den Windows normalerweise verwendet.

Wenn ein bestimmter Drucker benutzt werden soll, tragen Sie dessen Namen ein, zum Beispiel:

```json
"printerName": "Poststelle"
```

Das ist zum Beispiel sinnvoll, wenn die Dokumente in der Poststelle gedruckt und dort dem
Drucker entnommen und per Post versandt werden sollen. Der angegebene Drucker muss auf dem
Rechner verfügbar sein. Den genauen Namen finden Sie in den Windows-Druckereinstellungen.

### Welcher Ordner überwacht wird

Suchen Sie die Zeile `"downloadDirectory"`. Dort steht der Ordner, in den Ihr Browser die
heruntergeladenen Dateien speichert:

```json
"downloadDirectory": "y:/Download"
```

Genau dieser Ordner wird überwacht. Wenn Sie an einem anderen Ort speichern, passen Sie den
Pfad an.

---

## Start und Beenden

**Start:** Nach jedem Neustart des Computers bzw. des Desktops die Verknüpfung auf dem
Desktop **per Doppelklick starten**. Daraufhin erscheint kurz ein kleines Programmfenster.
Ab diesem Zeitpunkt läuft die MABS - PDF Sofortdruck Erweiterung im Hintergrund. Das
Programmfenster wird sofort minimiert und ist nur noch in der Taskleiste sichtbar – das ist
ganz normal.

**Beenden:** Falls es Probleme geben sollte, kann das Programm wie folgt beendet werden:
Klicken Sie in der Taskleiste mit der rechten Maustaste auf das Programmfenster und wählen
Sie **Schließen**. Nach dem nächsten Neustart des Rechners muss die Verknüpfung wieder per
Doppelklick gestartet werden.

**Hinweis:** Es kann immer nur eine Kopie des Programms laufen. Erscheint beim Starten die
Meldung „PDF Sofortdruck läuft bereits.", ist das Programm schon gestartet – Sie müssen
nichts tun.

---

## So drucken Sie ein Dokument aus MABS heraus bzw. schicken es an den Drucker der Poststelle, damit es dort per Brief versandt werden kann (Postversand manuell).

In der **MABS-Historie** auf die **Dokumentenvorschau** klicken – auf das kleine Auge-Symbol
in der Historienansicht. Daraufhin wird das Dokument angezeigt.

Danach auf die Schaltfläche **Herunterladen** klicken. Jetzt lädt der Browser das Dokument in
das eingestellte Download-Verzeichnis herunter.

Die MABS - PDF Sofortdruck Erweiterung sieht die heruntergeladene Datei und zeigt einen
kleinen Druck-Dialog an. Jetzt nur noch entweder auf die Schaltfläche **Drucken** klicken oder einfach
die **Return-Taste** drücken. Das Dokument wird dann automatisch an den eingestellten Drucker
gesendet.

Anschließend wird der MABS-Browser-Tab mit der Dokumentenvorschau wieder geschlossen, und Sie
finden sich wieder in der MABS-Historie, von der aus Sie den Vorgang gestartet haben.

### Es sind also nur noch 3 Klicks nötig

1. **Auge klicken** – Dokumentenvorschau öffnen
2. **Herunterladen klicken**
3. **Schaltfläche „Drucken" klicken** oder die **Return-Taste** betätigen

Fertig. Das Dokument ist gedruckt bzw. an die Poststelle geschickt.

Möchten Sie ein Dokument nicht drucken, klicken Sie im Druck-Dialog auf **Abbrechen**. Es wird
dann nichts gedruckt und die Datei bleibt erhalten.
