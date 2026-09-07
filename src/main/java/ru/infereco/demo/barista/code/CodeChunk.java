package ru.infereco.demo.barista.code;

public record CodeChunk(
        String repo,
        String path,
        String language,
        String symbol,
        String symbolType,
        int startLine,
        int endLine,
        String text
) {
}
