package com.northstar.api.assistant;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

/**
 * A {@link ChatMemory} that keeps the FULL transcript durably while feeding the
 * model only a recent window.
 *
 * <p>The stock {@link org.springframework.ai.chat.memory.MessageWindowChatMemory}
 * trims to its window on every {@code add} and {@code saveAll}-REPLACES the
 * conversation's rows — so after enough turns the older messages are physically
 * deleted from {@code spring_ai_chat_memory}, and {@code /history} (which
 * rehydrates the UI) and {@code /conversations} (titled by the first user
 * message) silently lose the start of the conversation. Here {@code add} only
 * ever appends, so the transcript is never lost; {@code get} returns just the
 * last {@code windowMessages} for the prompt, snapped forward to a whole user
 * turn so the model window never starts on a dangling assistant reply.
 */
final class WindowedChatMemory implements ChatMemory {

    private final ChatMemoryRepository repository;
    private final int windowMessages;

    WindowedChatMemory(ChatMemoryRepository repository, int windowMessages) {
        this.repository = repository;
        this.windowMessages = windowMessages;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // Append, never trim: the repository IS the durable transcript. saveAll
        // replaces the row set, so re-persist the existing messages plus the new.
        List<Message> all = new ArrayList<>(repository.findByConversationId(conversationId));
        all.addAll(messages);
        repository.saveAll(conversationId, all);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = repository.findByConversationId(conversationId);
        if (all.size() <= windowMessages) {
            return all;
        }
        int start = all.size() - windowMessages;
        // Snap forward to the nearest USER message so the window begins at a
        // complete turn, not on an assistant reply missing its prompt.
        while (start < all.size() && all.get(start).getMessageType() != MessageType.USER) {
            start++;
        }
        if (start == all.size()) {
            start = all.size() - windowMessages; // no user message in the tail — raw window
        }
        return new ArrayList<>(all.subList(start, all.size()));
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
