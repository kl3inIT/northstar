package com.northstar.core.study;

/**
 * Closed vocabulary carried in the provider JSON schema for word decomposition.
 * Constants stay lowercase because Spring AI emits enum names verbatim into that
 * external schema and existing card metadata uses the same lowercase values.
 */
public enum VocabWordPartKind {
    prefix,
    root,
    base,
    suffix
}
