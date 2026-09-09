package ru.infereco.demo.barista.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ConversationStore {
    private final Map<UUID, ChatSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root;

    public ConversationStore() { this(Path.of("data/chat-sessions")); }

    ConversationStore(Path root) {
        this.root = root;
        if (!Files.isDirectory(root)) return;
        try (var paths = Files.list(root)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                Snapshot snapshot = mapper.readValue(path.toFile(), Snapshot.class);
                ChatSession session = new ChatSession(snapshot.id(), Instant.parse(snapshot.createdAt()));
                session.restore(snapshot.context(), snapshot.mode(), snapshot.skill(),
                        snapshot.messages().stream().map(StoredMessage::restore).toList(),
                        snapshot.screenBrief(), snapshot.screenAt() == null ? null : Instant.parse(snapshot.screenAt()));
                attach(session);
                sessions.put(session.id(), session);
            }
        } catch (IOException ex) { throw new UncheckedIOException("Cannot load chat sessions", ex); }
    }

    public ChatSession create() { return create(null); }

    public ChatSession create(ChatContext context) {
        ChatSession session = new ChatSession(UUID.randomUUID(), Instant.now());
        session.setContext(context);
        attach(session);
        save(session);
        sessions.put(session.id(), session);
        return session;
    }

    public Optional<ChatSession> findContext(String documentId, String changeId) {
        return sessions.values().stream().filter(session -> {
            ChatContext context = session.context();
            return documentId == null ? context == null
                    : context != null && documentId.equals(context.documentId()) && changeId.equals(context.changeId());
        }).min(Comparator.comparing(ChatSession::createdAt));
    }

    private void attach(ChatSession session) { session.onChange(() -> save(session)); }

    private void save(ChatSession session) {
        synchronized (session) {
            try {
                Files.createDirectories(root);
                Path destination = root.resolve(session.id() + ".json");
                Path temp = Files.createTempFile(root, session.id() + "-", ".tmp");
                try {
                    mapper.writeValue(temp.toFile(), new Snapshot(session.id(), session.createdAt().toString(),
                            session.context(), session.mode(), session.skill(),
                            session.messages().stream().map(StoredMessage::from).toList(),
                            session.screenBrief(), session.screenAt() == null ? null : session.screenAt().toString()));
                    try {
                        Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally { Files.deleteIfExists(temp); }
            } catch (IOException ex) { throw new UncheckedIOException("Cannot save chat session", ex); }
        }
    }

    public Optional<ChatSession> find(UUID id) { return Optional.ofNullable(sessions.get(id)); }
    public ChatSession require(UUID id) { return find(id).orElseThrow(() -> new SessionNotFoundException(id)); }

    record Snapshot(UUID id, String createdAt, ChatContext context, String mode, String skill,
            List<StoredMessage> messages, String screenBrief, String screenAt) {}
    record StoredMessage(UUID id, String role, String text, String source, String at,
            Long latencyMs, String openUrl, String cardUrl) {
        static StoredMessage from(ChatMessage message) {
            return new StoredMessage(message.id(), message.role(), message.text(), message.source(),
                    message.at().toString(), message.latencyMs(), message.openUrl(), message.cardUrl());
        }
        ChatMessage restore() {
            return new ChatMessage(id, role, text, source, Instant.parse(at), latencyMs, openUrl, cardUrl);
        }
    }
}
