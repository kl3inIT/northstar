package com.northstar.core.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/** Provider-neutral routing port used by model-backed core features. */
public interface AiClientRouter {

    AiRoute route(AiTask task);

    ChatClient client(AiTask task);

    ChatClient client(AiRoute route);

    ChatModel model(AiRoute route);

    void validate(AiTask task, AiRoute route);
}
