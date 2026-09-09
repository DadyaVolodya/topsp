package ru.infereco.demo.barista.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meet")
public record MeetProperties(
        String topic,
        String systemPrompt,
        String doomTopic,
        String doomSystemPrompt,
        String screenPrompt,
        Models models,
        Voice voice,
        Improve improve,
        Speed speed,
        Style style,
        Demo demo,
        Stt stt,
        Specs specs,
        Telegram telegram,
        Code code,
        Ollama ollama
) {
    public record Models(String chat, String critic, String fast, String vision) {
    }

    public record Voice(int pauseMs, int maxUtteranceMs, int hintIdleMs) {
    }

    public record Improve(double reviseBelow, int maxRevisions, Boolean loopEnabled, Integer loopDelaySec) {
        public boolean loopOn() {
            return !Boolean.FALSE.equals(loopEnabled);
        }

        public int loopDelaySeconds() {
            return loopDelaySec == null || loopDelaySec < 15 ? 50 : loopDelaySec;
        }
    }

    public record Speed(double temperature, int chatMaxTokens, int hintMaxTokens, int screenMaxTokens, int historyMessages) {
    }

    public record Style(int maxSentences, boolean allowMarkdown) {
    }

    public record Demo(Pto pto) {
    }

    public record Pto(String url, String api, String login, String password) {
    }

    public record Stt(int sampleRate, String modelDir, String modelUrl) {
    }

    public record Specs(String dir, String tasksDir, String file) {
    }

    public record Telegram(String token, String chatId) {
    }

    public record Code(List<String> dirs) {
    }

    /** Fallback LLM: локальный Ollama или cloud с бесплатным ключом ollama.com/settings/keys. */
    public record Ollama(Boolean enabled, String baseUrl, String apiKey, String chat, String fast, String vision) {
    }

    public String fullSystemPrompt() {
        return withStyle(systemPrompt());
    }

    public String fullDoomSystemPrompt() {
        return withStyle(doomSystemPrompt());
    }

    public String fullScreenPrompt() {
        return screenPrompt().trim()
                + " Кадр только для Doom. Пиши обычными предложениями, без markdown.";
    }

    private String withStyle(String prompt) {
        String markdown = style.allowMarkdown() ? "" : " Без markdown, без списков, без заголовков.";
        return prompt.trim()
                + " Жёсткий лимит: не больше " + style.maxSentences() + " коротких предложений."
                + markdown;
    }
}
