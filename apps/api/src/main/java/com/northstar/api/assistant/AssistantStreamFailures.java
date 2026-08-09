package com.northstar.api.assistant;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a failed turn into one short, actionable sentence.
 *
 * <p>A failed turn used to end as a fixed "stream failed" frame, so a model that
 * cannot serve this chat was indistinguishable from a broken deployment. Only
 * the HTTP status is read off the failure and every returned sentence is a fixed
 * string: a chatty or misconfigured gateway can never echo a key, a prompt
 * fragment, or provider internals into the browser.
 *
 * <p>The status is parsed from the message prefix that HTTP clients put there
 * ({@code "400: ..."} from the OpenAI SDK, {@code "404 Not Found: ..."} from
 * Spring) instead of matching provider exception types. The provider SDK belongs
 * to the AI integration module, and the delivery layer only needs to know which
 * status came back.
 */
final class AssistantStreamFailures {

    static final String GENERIC = "The assistant stream failed.";

    /** A leading three-digit status, as HTTP client exceptions render it. */
    private static final Pattern STATUS_PREFIX = Pattern.compile("^\\s*([45]\\d{2})(?::|\\s)");
    private static final int MAX_CAUSE_DEPTH = 10;

    private AssistantStreamFailures() {
    }

    static String describe(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            int status = status(cause.getMessage());
            if (status > 0) {
                return forStatus(status);
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return GENERIC;
    }

    private static int status(String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = STATUS_PREFIX.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    static String forStatus(int status) {
        return switch (status) {
            case 400, 422 -> "The selected model rejected this request. "
                    + "Pick a different chat model and send it again.";
            case 401, 403 -> "The AI gateway rejected its credentials. Update its key in Settings.";
            case 404 -> "The selected model is no longer available on this gateway. Pick another model.";
            case 408, 504 -> "The AI gateway did not answer in time. Send the message again.";
            case 429 -> "The AI gateway is rate limiting requests. Send the message again in a moment.";
            default -> status >= 500
                    ? "The AI gateway failed while answering. Send the message again."
                    : "The AI gateway rejected this request. Check the model and gateway in Settings.";
        };
    }
}
