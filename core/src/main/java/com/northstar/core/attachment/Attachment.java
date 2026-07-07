package com.northstar.core.attachment;

import com.northstar.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One stored file. Rows are immutable after upload (content-addressed by
 * sha256), so serving can send far-future cache headers keyed on the id.
 */
@Entity
@Table(name = "attachment")
public class Attachment extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String filename;

    @NotBlank
    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @NotBlank
    @Column(nullable = false, length = 64)
    private String sha256;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false)
    private StorageType storageType = StorageType.DATABASE;

    /** Path/key for external backends; null for DATABASE rows. */
    @Column
    private @Nullable String reference;

    /** The bytes themselves for DATABASE rows; null for external backends. */
    @Column(columnDefinition = "bytea")
    private byte @Nullable [] data;

    protected Attachment() {
        // for JPA
    }

    Attachment(UUID id, String filename, String mimeType, String sha256, byte[] data) {
        super(id);
        this.filename = filename;
        this.mimeType = mimeType;
        this.sizeBytes = data.length;
        this.sha256 = sha256;
        this.data = data;
    }

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public @Nullable String getReference() {
        return reference;
    }

    public byte @Nullable [] getData() {
        return data;
    }
}
