package com.northstar.api.attachment;

import com.northstar.core.attachment.AttachmentService;
import com.northstar.core.attachment.AttachmentTypePolicy;
import com.northstar.core.attachment.AttachmentView;
import com.northstar.core.search.AttachmentIndexView;
import com.northstar.core.search.SearchService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
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

    /**
     * The ONLY types ever rendered inline, and they must prove it by magic
     * bytes at upload. Everything else downloads as an attachment under a
     * sandboxing CSP — an uploaded SVG/HTML must never execute on this origin.
     */
    private static final Set<String> INLINE_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final AttachmentService attachments;
    private final SearchService search;

    AttachmentController(AttachmentService attachments, SearchService search) {
        this.attachments = attachments;
        this.search = search;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadAttachment")
    AttachmentView upload(@RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file", e);
        }
        var accepted = AttachmentTypePolicy.inspect(file.getOriginalFilename(), bytes);
        return attachments.store(file.getOriginalFilename(), accepted.mimeType(), bytes);
    }

    /** Worker preparation state for files queued in the Assistant composer. */
    @GetMapping("/index-status")
    @Operation(operationId = "listAttachmentIndexStatus")
    List<AttachmentIndexView> indexStatus(@RequestParam("ids") List<UUID> ids) {
        if (ids.isEmpty() || ids.size() > 3) {
            throw new IllegalArgumentException("Request between 1 and 3 attachment ids");
        }
        return search.attachmentIndexStatuses(ids);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "serveAttachment")
    ResponseEntity<byte[]> serve(@PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        // Metadata-only lookup first: a conditional revalidation that ends in 304
        // must not drag the whole (up-to-25MB) bytea out of Postgres and the heap.
        AttachmentView meta = attachments.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No file " + id));
        String etag = "\"" + meta.sha256() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        byte[] data = attachments.load(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No file " + id))
                .data();
        boolean inlineImage = INLINE_IMAGE_TYPES.contains(meta.mimeType().toLowerCase(Locale.ROOT));
        ContentDisposition disposition = (inlineImage ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(meta.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                // Belt and braces against stored XSS: the browser must not
                // second-guess the type, and even a hostile body is inert.
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .contentType(inlineImage ? MediaType.parseMediaType(meta.mimeType())
                        : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(data);
    }
}
