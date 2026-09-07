package ru.infereco.demo.barista.chat;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ConversationStore {

    private final Map<UUID, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSession create() {
        ChatSession session = new ChatSession(UUID.randomUUID(), Instant.now());
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<ChatSession> find(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public ChatSession require(UUID id) {
        return find(id).orElseThrow(() -> new SessionNotFoundException(id));
    }
}
