package com.northstar.core.attachment;

/** Where an attachment's bytes live; the metadata row is the same either way. */
public enum StorageType {
    /** Bytes in the row's {@code data} column — the only backend implemented today. */
    DATABASE,
    /** Bytes on the local filesystem; {@code reference} is the path. (Reserved.) */
    LOCAL,
    /** Bytes in an S3-compatible bucket; {@code reference} is the key. (Reserved.) */
    S3
}
