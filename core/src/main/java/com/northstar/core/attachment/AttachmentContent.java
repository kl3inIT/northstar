package com.northstar.core.attachment;

/** An attachment ready to serve: metadata plus its bytes. */
public record AttachmentContent(AttachmentView meta, byte[] data) {
}
