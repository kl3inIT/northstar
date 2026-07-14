package com.northstar.core.search;

/** One bounded, untrusted document excerpt supplied to an Assistant turn. */
public record AttachmentExcerpt(String filename, String locator, String url, String text) {
}
