package com.northstar.core.cache;

import java.util.ArrayList;
import java.util.List;

public final class SemanticCachePolicy {

    public SemanticCacheDecision evaluate(SemanticCacheContext context) {
        if (context == null) {
            return new SemanticCacheDecision(false, List.of(SemanticCacheRejection.INCOMPLETE_CONTEXT));
        }
        List<SemanticCacheRejection> rejections = new ArrayList<>();
        rejectUnless(context.enabled(), SemanticCacheRejection.DISABLED, rejections);
        rejectUnless(context.readOnly(), SemanticCacheRejection.NOT_READ_ONLY, rejections);
        rejectUnless(context.contextComplete(), SemanticCacheRejection.INCOMPLETE_CONTEXT, rejections);
        rejectUnless(context.toolFree(), SemanticCacheRejection.USES_TOOLS, rejections);
        rejectUnless(context.memoryFree(), SemanticCacheRejection.USES_MEMORY, rejections);
        rejectUnless(context.attachmentFree(), SemanticCacheRejection.HAS_ATTACHMENTS, rejections);
        rejectUnless(context.liveDataIndependent(), SemanticCacheRejection.DEPENDS_ON_LIVE_DATA, rejections);
        rejectUnless(context.evidenceInsensitive(), SemanticCacheRejection.EVIDENCE_SENSITIVE, rejections);
        return new SemanticCacheDecision(rejections.isEmpty(), rejections);
    }

    private static void rejectUnless(boolean condition, SemanticCacheRejection rejection,
            List<SemanticCacheRejection> rejections) {
        if (!condition) rejections.add(rejection);
    }
}
