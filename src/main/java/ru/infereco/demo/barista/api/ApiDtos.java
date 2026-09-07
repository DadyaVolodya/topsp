package ru.infereco.demo.barista.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import ru.infereco.demo.barista.chat.ChatMessage;
import ru.infereco.demo.barista.chat.ChatSession;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record CreateSessionResponse(UUID id, String topic, String skill, Instant createdAt, List<MessageResponse> messages) {
        public static CreateSessionResponse from(ChatSession session, String topic) {
            return new CreateSessionResponse(
                    session.id(),
                    topic,
                    session.skill(),
                    session.createdAt(),
                    session.messages().stream().map(message -> MessageResponse.from(message, session.skill())).toList());
        }
    }

    public record SessionResponse(UUID id, String skill, Instant createdAt, List<MessageResponse> messages) {
        public static SessionResponse from(ChatSession session) {
            return new SessionResponse(
                    session.id(),
                    session.skill(),
                    session.createdAt(),
                    session.messages().stream().map(message -> MessageResponse.from(message, session.skill())).toList());
        }
    }

    public record MessageRequest(@NotBlank String text, String source) {
    }

    public record HintRequest(Integer elapsedSec, String lastCheat, Boolean playing) {
    }

    public record ScreenRequest(String image) {
    }

    public record MessageResponse(
            UUID id,
            String role,
            String text,
            String source,
            Instant at,
            Long latencyMs,
            String skill,
            String openUrl,
            String cardUrl) {
        public static MessageResponse from(ChatMessage message) {
            return from(message, "");
        }

        public static MessageResponse from(ChatMessage message, String skill) {
            return new MessageResponse(
                    message.id(),
                    message.role(),
                    message.text(),
                    message.source(),
                    message.at(),
                    message.latencyMs(),
                    skill == null ? "" : skill,
                    message.openUrl(),
                    message.cardUrl());
        }
    }

    public record ModelsResponse(String chat, String critic, String fast, String vision, String topic) {
    }

    public record VoiceSettingsResponse(int pauseMs, int maxUtteranceMs, int hintIdleMs) {
    }

    public record TuningResponse(
            String topic,
            String chatModel,
            String criticModel,
            String hintModel,
            double temperature,
            int chatMaxTokens,
            int hintMaxTokens,
            int historyMessages,
            int maxSentences,
            boolean allowMarkdown,
            int hintIdleMs,
            int maxRevisions,
            String systemPrompt
    ) {
    }
}
