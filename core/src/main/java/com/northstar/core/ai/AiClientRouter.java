package com.northstar.core.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/** Provider-neutral routing port used by model-backed core features. */
public interface AiClientRouter {

    AiRoute route(AiTask task);

    ChatClient client(AiTask task);

    ChatClient client(AiRoute route);

    ChatModel model(AiRoute route);

    /**
     * Provider-aware deterministic options for one non-streaming structured-output call.
     * Implementations must preserve the provider's token-field and temperature rules.
     */
    ChatOptions.Builder<?> structuredOutputOptions(AiRoute route, int maxTokens);

    void validate(AiTask task, AiRoute route);
}
