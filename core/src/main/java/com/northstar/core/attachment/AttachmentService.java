package com.northstar.core.attachment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The attachment module's public API: store bytes once (sha256-deduplicated),
 * read them back by id. Rows are immutable — "updating" a file is uploading a
 * new one and referencing its new id.
 */
@Service
public class AttachmentService {

    /** Matches the multipart cap in the api's application.yml. */
    public static final int MAX_BYTES = 25 * 1024 * 1024;

    private final AttachmentRepository attachments;

    AttachmentService(AttachmentRepository attachments) {
        this.attachments = attachments;
    }

    /** Stores the file, or returns the existing row when these exact bytes are already kept. */
    @Transactional
    public AttachmentView store(String filename, String mimeType, byte[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Attachment is empty");
        }
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("Attachment exceeds the 25MB limit");
        }
        String cleanName = safeFilename(filename);
        String cleanMime = mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType.strip();
        String hash = sha256(data);
        return attachments.findBySha256(hash)
                .map(this::view)
                .orElseGet(() -> view(attachments.save(
                        new Attachment(UUID.randomUUID(), cleanName, cleanMime, hash, data))));
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentContent> load(UUID id) {
        return attachments.findById(id).map(a -> {
            byte[] data = a.getData();
            if (data == null) {
                // LOCAL/S3 backends are reserved, not implemented — see StorageType.
                throw new IllegalStateException(
                        "Attachment " + id + " uses unsupported storage " + a.getStorageType());
            }
            return new AttachmentContent(view(a), data);
        });
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentView> find(UUID id) {
        return attachments.findById(id).map(this::view);
    }

    private AttachmentView view(Attachment a) {
        return new AttachmentView(a.getId(), a.getFilename(), a.getMimeType(), a.getSizeBytes(),
                a.getSha256(), a.getCreatedAt());
    }

    /** Strips any path segments a client smuggles into the filename. */
    private static String safeFilename(String filename) {
        String name = filename == null ? "" : filename.strip();
        int cut = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (cut >= 0) {
            name = name.substring(cut + 1);
        }
        return name.isBlank() ? "file" : name;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM without SHA-256", e); // spec-mandated, unreachable
        }
    }
}
