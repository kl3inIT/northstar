package com.northstar.core.attachment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.tika.Tika;

/**
 * One shared allowlist for user-uploaded Assistant/note files. The browser's
 * {@code accept} attribute is only a picker hint; this policy verifies the
 * bytes and filename again at the server boundary.
 */
public final class AttachmentTypePolicy {

    private static final Tika TIKA = new Tika();

    private static final Map<String, String> RASTER_MIMES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp");

    private static final Map<String, String> DOCUMENT_MIMES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("csv", "text/csv"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"));

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "c", "cc", "cpp", "cs", "css", "dart", "go", "gradle", "graphql",
            "h", "hpp", "java", "js", "jsx", "kt", "kts", "php", "properties",
            "py", "rb", "rs", "scala", "scss", "sh", "sql", "svelte", "swift",
            "toml", "ts", "tsx", "vue", "yaml", "yml", "ps1");

    private static final Set<String> OOXML_EXTENSIONS = Set.of("docx", "pptx", "xlsx");
    private static final Set<String> LEGACY_OFFICE_EXTENSIONS = Set.of("doc", "ppt", "xls");
    private static final Set<String> TEXT_DOCUMENT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "csv", "html", "htm", "json", "xml");

    private AttachmentTypePolicy() {
    }

    /** Verifies and normalizes one upload. Throws a safe client-facing error when rejected. */
    public static AcceptedType inspect(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Attachment is empty");
        }
        String extension = extension(filename);
        String imageMime = rasterMime(bytes);
        if (RASTER_MIMES.containsKey(extension)) {
            String expected = RASTER_MIMES.get(extension);
            if (imageMime == null || !imageMime.equals(expected)) {
                throw new IllegalArgumentException("Image bytes do not match the filename");
            }
            return new AcceptedType(imageMime, Kind.IMAGE);
        }
        if (imageMime != null) {
            return new AcceptedType(imageMime, Kind.IMAGE);
        }

        boolean source = SOURCE_EXTENSIONS.contains(extension);
        String canonical = DOCUMENT_MIMES.get(extension);
        if (!source && canonical == null) {
            throw new IllegalArgumentException("Unsupported attachment type");
        }

        String detected = TIKA.detect(bytes, filename == null ? "file" : filename)
                .toLowerCase(Locale.ROOT);
        if (source || TEXT_DOCUMENT_EXTENSIONS.contains(extension)) {
            requireUtf8Text(bytes);
            return new AcceptedType(source ? "text/plain" : canonical, Kind.DOCUMENT);
        }
        if (extension.equals("pdf") && !detected.equals("application/pdf")) {
            throw new IllegalArgumentException("PDF bytes do not match the filename");
        }
        if (extension.equals("rtf") && !detected.contains("rtf")) {
            throw new IllegalArgumentException("RTF bytes do not match the filename");
        }
        if (OOXML_EXTENSIONS.contains(extension)
                && !detected.equals(canonical)) {
            throw new IllegalArgumentException("Office document bytes do not match the filename");
        }
        if (LEGACY_OFFICE_EXTENSIONS.contains(extension)
                && !(detected.equals(canonical) || detected.equals("application/x-tika-msoffice"))) {
            throw new IllegalArgumentException("Office document bytes do not match the filename");
        }
        return new AcceptedType(canonical, Kind.DOCUMENT);
    }

    /** Metadata-only hint for rendering; byte inspection remains authoritative. */
    public static boolean isImageMime(String mimeType) {
        return mimeType != null && RASTER_MIMES.containsValue(mimeType.toLowerCase(Locale.ROOT));
    }

    private static void requireUtf8Text(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                throw new IllegalArgumentException("Text attachment contains binary data");
            }
        }
        try (var reader = new InputStreamReader(new ByteArrayInputStream(bytes),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            char[] buffer = new char[4096];
            while (reader.read(buffer) != -1) {
                // Incremental decode validates UTF-8 without a full-size CharBuffer.
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Text attachment must use UTF-8", e);
        }
    }

    private static String extension(String filename) {
        String name = filename == null ? "" : filename.strip().toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        int dot = name.lastIndexOf('.');
        return dot > slash && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }

    private static String rasterMime(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public enum Kind {
        IMAGE,
        DOCUMENT
    }

    public record AcceptedType(String mimeType, Kind kind) {
    }
}
