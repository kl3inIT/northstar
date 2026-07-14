package com.northstar.core.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.northstar.core.attachment.AttachmentContent;
import com.northstar.core.attachment.AttachmentView;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;

class AttachmentDocumentReaderTests {

    private final AttachmentDocumentReader reader = new AttachmentDocumentReader();

    @Test
    void pdfRetainsTruthfulPageNumbers() throws Exception {
        byte[] bytes = twoPagePdf();
        AttachmentView metadata = new AttachmentView(
                UUID.randomUUID(), "reader-proof.pdf", "application/pdf",
                bytes.length, "sha256", Instant.parse("2026-07-14T00:00:00Z"));

        List<Document> pages = reader.read(new AttachmentContent(metadata, bytes), "application/pdf");

        assertThat(pages).hasSize(2);
        assertThat(normalizeWhitespace(pages.get(0).getText())).contains("First page evidence");
        assertThat(pages.get(0).getMetadata())
                .containsEntry(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, 1)
                .containsEntry(PagePdfDocumentReader.METADATA_FILE_NAME, "reader-proof.pdf");
        assertThat(normalizeWhitespace(pages.get(1).getText())).contains("Second page evidence");
        assertThat(pages.get(1).getMetadata())
                .containsEntry(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, 2);
    }

    private static byte[] twoPagePdf() throws Exception {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addPage(pdf, "First page evidence");
            addPage(pdf, "Second page evidence");
            pdf.save(output);
            return output.toByteArray();
        }
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").strip();
    }

    private static void addPage(PDDocument pdf, String text) throws Exception {
        PDPage page = new PDPage();
        pdf.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(72, 720);
            content.showText(text);
            content.endText();
        }
    }
}
