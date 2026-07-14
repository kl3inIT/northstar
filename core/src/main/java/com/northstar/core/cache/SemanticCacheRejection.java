package com.northstar.core.cache;

public enum SemanticCacheRejection {
    DISABLED,
    NOT_READ_ONLY,
    INCOMPLETE_CONTEXT,
    USES_TOOLS,
    USES_MEMORY,
    HAS_ATTACHMENTS,
    DEPENDS_ON_LIVE_DATA,
    EVIDENCE_SENSITIVE
}
