package ru.infereco.demo.barista.spec;

public record RequirementCodeLink(
        String changeId,
        int specVersion,
        String repo,
        String file,
        String symbol,
        String reason,
        double confidence
) {
}
