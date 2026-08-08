package com.contractguard.service;

import com.contractguard.exception.PdfProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Turns PDF bytes into plain text.
 *
 * Implemented for you because it is mechanical. Read it, but the interesting
 * work is in the two services below it.
 */
@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            if (document.isEncrypted()) {
                throw new PdfProcessingException("Password-protected PDFs are not supported");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // helps with multi-column layouts
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                // Almost always a scanned image PDF with no text layer.
                throw new PdfProcessingException(
                        "No selectable text found. This looks like a scanned document; "
                                + "OCR would be required to read it.");
            }

            log.debug("Extracted {} characters from {} pages",
                    text.length(), document.getNumberOfPages());
            return normalise(text);

        } catch (IOException ex) {
            throw new PdfProcessingException("Could not read the PDF file", ex);
        }
    }

    /**
     * Joins words that PDF extraction split across lines and collapses runs of
     * blank lines, without destroying the paragraph breaks the segmenter needs.
     */
    private String normalise(String raw) {
        return raw
                .replace("\r\n", "\n")
                .replaceAll("(\\w)-\\n(\\w)", "$1$2")   // de-hyphenate line breaks
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
