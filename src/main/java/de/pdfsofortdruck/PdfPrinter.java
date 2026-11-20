package de.pdfsofortdruck;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.printing.Orientation;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.OrientationRequested;
import java.awt.print.PrinterJob;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Sendet eine PDF-Datei ohne weiteren Dialog an den eingestellten Standarddrucker. */
public final class PdfPrinter {

    private PdfPrinter() {
    }

    public static void print(Path pdf, WatcherConfig config, Consumer<String> logger) throws Exception {
        String printerName = config.printerName();
        PrintService printer = findPrinter(printerName);
        if (printer == null) {
            if (printerName == null || printerName.isBlank()) {
                throw new IllegalStateException("Es ist kein Standarddrucker eingestellt.");
            }
            throw new IllegalStateException("Drucker \"" + printerName + "\" wurde nicht gefunden.");
        }

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer); // Kein printDialog() -> direkt auf den Standarddrucker.

            // Schritt 2 (optional): feste Render-DPI für konsistenteres Rendering.
            // printDpi==0 entspricht dem bisherigen Standardverhalten.
            if (config.printDpi() > 0) {
                job.setPageable(new PDFPageable(document, Orientation.AUTO, false, (float) config.printDpi()));
                logger.accept("Druck: PDFPageable mit fester DPI=" + config.printDpi());
            } else {
                job.setPageable(new PDFPageable(document));
                logger.accept("Druck: PDFPageable mit Standard-DPI (aktuelles Verhalten)");
            }

            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            attributes.add(new Copies(1));

            // Schritt 1 (optional): Medienformat/Ausrichtung explizit aus der PDF anfordern,
            // um treiberabhängige Default-Formate zu reduzieren.
            if (config.setMediaAttributesFromPdf()) {
                applyMediaAttributes(document, attributes, logger);
            } else {
                logger.accept("Druck: Medienattribute aus PDF nicht gesetzt (aktuelles Verhalten)");
            }

            logger.accept("Druck an \"" + printer.getName() + "\" wird gesendet.");
            job.print(attributes);
        }
    }

    private static void applyMediaAttributes(PDDocument document, PrintRequestAttributeSet attributes,
                                             Consumer<String> logger) {
        if (document.getNumberOfPages() < 1) {
            logger.accept("Druck: PDF ohne Seiten – keine Medienattribute gesetzt.");
            return;
        }
        try {
            PDPage page = document.getPage(0);
            PDRectangle box = page.getCropBox();
            if (box == null) {
                box = page.getMediaBox();
            }
            float wPt = box.getWidth();
            float hPt = box.getHeight();
            int rotation = page.getRotation();
            boolean rotated = (Math.abs(rotation) % 180) == 90;
            boolean landscape = rotated ? (wPt < hPt) : (wPt > hPt);

            OrientationRequested orientation = landscape
                    ? OrientationRequested.LANDSCAPE
                    : OrientationRequested.PORTRAIT;
            attributes.add(orientation);

            // Physische Blattgröße in Inch für die Medienerkennung bestimmen.
            float sheetWPts = landscape ? Math.max(wPt, hPt) : Math.min(wPt, hPt);
            float sheetHPts = landscape ? Math.min(wPt, hPt) : Math.max(wPt, hPt);
            float sheetWIn = sheetWPts / 72f;
            float sheetHIn = sheetHPts / 72f;

            MediaSizeName mediaName = MediaSize.findMedia(sheetWIn, sheetHIn, Size2DSyntax.INCH);
            if (mediaName != null) {
                attributes.add(mediaName);
                logger.accept(String.format(
                        "Druck: Medienattribute aus PDF – Seite %.2fx%.2fpt, Rotation %d, %s, Medienformat %s",
                        wPt, hPt, rotation, orientation, mediaName));
            } else {
                // Kein Standardname gefunden; Orientierung trotzdem gesetzt.
                logger.accept(String.format(
                        "Druck: Medienattribute aus PDF – Seite %.2fx%.2fpt, Rotation %d, %s, kein Standard-Medienformat erkannt",
                        wPt, hPt, rotation, orientation));
            }
        } catch (Exception e) {
            logger.accept("Druck: Medienattribute konnten nicht ermittelt werden: " + e);
        }
    }

    private static PrintService findPrinter(String printerName) {
        if (printerName == null || printerName.isBlank()) {
            return PrintServiceLookup.lookupDefaultPrintService();
        }
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(printerName.trim())) {
                return service;
            }
        }
        return null;
    }
}
