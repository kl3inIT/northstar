package com.northstar.api.assistant;

/**
 * The subset of the Vercel AI SDK UI Message Stream parts this backend emits.
 * The web app's `useChat` consumes these frames directly, which is what lets
 * the ai-elements components (Message, Tool) render streaming text and live
 * tool calls without a custom client protocol.
 */
sealed interface Part {

    record StartStep() implements Part {
    }

    record FinishStep() implements Part {
    }

    record TextStart(String id) implements Part {
    }

    record TextDelta(String id, String delta) implements Part {
    }

    record TextEnd(String id) implements Part {
    }

    record ToolInputStart(String toolCallId, String toolName) implements Part {
    }

    record ToolInputAvailable(String toolCallId, String toolName, Object input) implements Part {
    }

    record ToolOutputAvailable(String toolCallId, Object output) implements Part {
    }

    record ToolOutputError(String toolCallId, String errorText) implements Part {
    }

    record SourceUrl(String sourceId, String url, String title) implements Part {
    }

    record SourceDocument(String sourceId, String mediaType, String title, String filename) implements Part {
    }
}
