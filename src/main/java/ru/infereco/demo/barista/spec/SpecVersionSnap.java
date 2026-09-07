package ru.infereco.demo.barista.spec;

import java.time.Instant;
import java.util.List;

public record SpecVersionSnap(
        String documentId,
        String fileName,
        int version,
        long fingerprint,
        String createdAt,
        String rawText,
        List<SpecSection> sections
) {
    public Instant createdInstant() {
        if (createdAt == null || createdAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(createdAt);
        } catch (Exception ex) {
            return null;
        }
    }
}
