package ru.infereco.demo.barista.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        String role,
        String text,
        String source,
        Instant at,
        Long latencyMs,
        String openUrl,
        String cardUrl
) {
}
