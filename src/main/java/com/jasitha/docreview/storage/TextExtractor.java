package com.jasitha.docreview.storage;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pulls plain text out of uploaded PDFs and text files. */
@Component
public class TextExtractor {

    public String extract(Path file, String contentType) throws IOException {
        if ("application/pdf".equals(contentType)) {
            try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
