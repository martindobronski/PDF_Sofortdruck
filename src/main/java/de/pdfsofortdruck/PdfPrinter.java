package de.pdfsofortdruck;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.awt.print.PrinterJob;
import java.nio.file.Path;

/** Sendet eine PDF-Datei ohne weiteren Dialog an den eingestellten Standarddrucker. */
public final class PdfPrinter {

    private PdfPrinter() {
    }

    public static void print(Path pdf, String printerName) throws Exception {
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
            job.setPrintable(new PDFPrintable(document, Scaling.SHRINK_TO_FIT));
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            attributes.add(new Copies(1));
            job.print(attributes);
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
