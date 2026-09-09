package ru.infereco.demo.barista.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSession {

    private final UUID id;
    private final Instant createdAt;
    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatContext context;
    private Runnable onChange = () -> {};
    private String mode = "meet";
    private String skill = "topsp";
    private String screenBrief = "";
    private Instant screenAt;

    public ChatSession(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public synchronized ChatContext context() { return context; }

    synchronized void restore(ChatContext context, String mode, String skill, List<ChatMessage> messages,
            String screenBrief, Instant screenAt) {
        this.context = context;
        this.mode = mode;
        this.skill = skill;
        this.messages.addAll(messages);
        this.screenBrief = screenBrief;
        this.screenAt = screenAt;
    }

    synchronized void onChange(Runnable listener) { this.onChange = listener; }

    synchronized void setContext(ChatContext context) { this.context = context; onChange.run(); }

    public synchronized String mode() {
        return mode;
    }

    public synchronized void setMode(String mode) {
        this.mode = mode == null || mode.isBlank() ? "meet" : mode;
        if ("doom".equals(this.mode)) {
            this.skill = "doom";
        }
    }

    public synchronized String skill() {
        return skill == null || skill.isBlank() ? "topsp" : skill;
    }

    public synchronized void setSkill(String skill) {
        if ("doom".equals(mode)) {
            this.skill = "doom";
            return;
        }
        this.skill = skill == null || skill.isBlank() ? "topsp" : skill;
        onChange.run();
    }

    public synchronized boolean doom() {
        return "doom".equals(mode);
    }

    public UUID id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    public synchronized ChatMessage add(String role, String text, String source, Long latencyMs) {
        return add(role, text, source, latencyMs, null, null);
    }

    public synchronized ChatMessage add(
            String role, String text, String source, Long latencyMs, String openUrl, String cardUrl) {
        ChatMessage message = new ChatMessage(
                UUID.randomUUID(), role, text, source, Instant.now(), latencyMs, openUrl, cardUrl);
        messages.add(message);
        onChange.run();
        return message;
    }

    public synchronized void rememberScreen(String brief) {
        this.screenBrief = brief == null ? "" : brief.trim();
        this.screenAt = Instant.now();
        onChange.run();
    }

    public synchronized String screenBrief() {
        return screenBrief == null ? "" : screenBrief;
    }

    public synchronized boolean hasScreen() {
        return screenBrief != null && !screenBrief.isBlank();
    }

    public synchronized Instant screenAt() {
        return screenAt;
    }

    public synchronized boolean sameScreen(String brief) {
        if (brief == null || brief.isBlank() || screenBrief.isBlank()) {
            return false;
        }
        return normalize(screenBrief).equals(normalize(brief));
    }

    private static String normalize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
