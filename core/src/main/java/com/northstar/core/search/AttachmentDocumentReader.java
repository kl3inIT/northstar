package com.northstar.core.search;

import com.northstar.core.attachment.AttachmentContent;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;

/** Selects the narrowest truthful Spring AI reader for one accepted attachment. */
final class AttachmentDocumentReader {

    List<Document> read(AttachmentContent content, String mimeType) {
        String filename = content.meta().filename().toLowerCase(Locale.ROOT);
        if (mimeType.startsWith("text/") || mimeType.equals("application/json")
                || mimeType.equals("application/xml") || filename.endsWith(".md")) {
            return List.of(new Document(new String(content.data(), StandardCharsets.UTF_8)));
        }
        ByteArrayResource resource = new ByteArrayResource(content.data()) {
            @Override
            public String getFilename() {
                return content.meta().filename();
            }
        };
        List<Document> extracted = mimeType.equals("application/pdf")
                ? new PagePdfDocumentReader(resource).get()
                : new TikaDocumentReader(resource).get();
        return extracted.stream()
                .filter(document -> document.getText() != null && !document.getText().isBlank())
                .toList();
    }
}
