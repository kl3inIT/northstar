package com.northstar.api.attachment;

import com.northstar.core.attachment.AttachmentContent;
import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.attachment.AttachmentView;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Upload and serve stored files (chat images, note images, documents).
 * Attachments are immutable and sha256-deduplicated, so a served id is safe to
 * cache aggressively: ETag = content hash, far-future max-age.
 */
@RestController
@RequestMapping("/api/files")
class AttachmentController {

    private final AttachmentService attachments;

    AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentView upload(@RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file", e);
        }
        return attachments.store(file.getOriginalFilename(), file.getContentType(), bytes);
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> serve(@PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        AttachmentContent content = attachments.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No file " + id));
        AttachmentView meta = content.meta();
        String etag = "\"" + meta.sha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .contentType(MediaType.parseMediaType(meta.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(meta.filename())
                        .build().toString())
                .body(content.data());
    }
}
