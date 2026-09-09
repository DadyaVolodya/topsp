package ru.infereco.demo.barista.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class InferecoClient {

    private static final Logger LOG = LoggerFactory.getLogger(InferecoClient.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;
    private final MeetProperties.Ollama ollama;
    private volatile String lastProvider = "none";
    private volatile String lastError = "";

    public InferecoClient(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.infereco.ru/v1}") String baseUrl,
            MeetProperties properties
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api.infereco.ru/v1"
                : baseUrl.replaceAll("/$", "");
        this.ollama = properties == null ? null : properties.ollama();
    }

    public String complete(String model, String system, List<Turn> history, String user, int maxTokens, double temperature) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model == null ? "" : model);
            body.put("max_tokens", Math.max(maxTokens, 16));
            body.put("temperature", temperature);
            ArrayNode messages = body.putArray("messages");
            if (system != null && !system.isBlank()) {
                ObjectNode sys = messages.addObject();
                sys.put("role", "system");
                sys.put("content", system);
            }
            if (history != null) {
                for (Turn turn : history) {
                    ObjectNode node = messages.addObject();
                    node.put("role", turn.role());
                    node.put("content", turn.content());
                }
            }
            ObjectNode userNode = messages.addObject();
            userNode.put("role", "user");
            userNode.put("content", user);
            return sendWithFallback(body, model, Duration.ofSeconds(45), false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM прервана", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "LLM failed" : ex.getMessage(), ex);
        }
    }

    public String completeVision(String model, String system, String user, String dataUrl, int maxTokens, double temperature) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model == null ? "" : model);
            body.put("max_tokens", Math.max(maxTokens, 16));
            body.put("temperature", temperature);
            ArrayNode messages = body.putArray("messages");
            if (system != null && !system.isBlank()) {
                ObjectNode sys = messages.addObject();
                sys.put("role", "system");
                sys.put("content", system);
            }
            ObjectNode userNode = messages.addObject();
            userNode.put("role", "user");
            ArrayNode content = userNode.putArray("content");
            ObjectNode text = content.addObject();
            text.put("type", "text");
            text.put("text", user == null || user.isBlank() ? "Что на экране и чем помочь прямо сейчас?" : user);
            ObjectNode image = content.addObject();
            image.put("type", "image_url");
            ObjectNode imageUrl = image.putObject("image_url");
            imageUrl.put("url", dataUrl);
            return sendWithFallback(body, model, Duration.ofSeconds(20), true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM прервана", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "LLM vision failed" : ex.getMessage(), ex);
        }
    }

    public StatusView status() {
        boolean ollamaOn = ollamaEnabled();
        return new StatusView(
                apiKey.isBlank() ? "нет ключа Infereco" : "Infereco",
                ollamaOn ? ollamaBase() : "",
                ollamaOn,
                ollamaOn ? ollamaModel(false) : "",
                lastProvider,
                lastError == null ? "" : lastError);
    }

    private String sendWithFallback(ObjectNode body, String model, Duration timeout, boolean vision)
            throws Exception {
        Exception primary = null;
        if (!apiKey.isBlank()) {
            try {
                String content = post(baseUrl, apiKey, body, model, timeout, "Infereco");
                lastProvider = "infereco";
                lastError = "";
                return content;
            } catch (Exception ex) {
                primary = ex;
                LOG.warn("Infereco fail model={}: {}", model, ex.getMessage());
            }
        } else {
            primary = new IllegalStateException("нет ключа Infereco");
        }
        if (!ollamaEnabled()) {
            throw primary;
        }
        String ollamaModel = vision ? ollamaModel(true) : mapToOllama(model);
        ObjectNode fallbackBody = body.deepCopy();
        fallbackBody.put("model", ollamaModel);
        LOG.warn("fallback Ollama model={} url={}", ollamaModel, ollamaBase());
        try {
            String content = post(ollamaBase(), ollamaKey(), fallbackBody, ollamaModel, timeout, "Ollama");
            lastProvider = "ollama";
            lastError = primary.getMessage() == null ? "" : primary.getMessage();
            return content;
        } catch (Exception ex) {
            lastProvider = "none";
            lastError = "Infereco: " + primary.getMessage() + "; Ollama: " + ex.getMessage();
            throw new IllegalStateException(lastError, ex);
        }
    }

    private String post(
            String endpoint,
            String bearer,
            ObjectNode body,
            String model,
            Duration timeout,
            String label
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint + "/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String snippet = response.body() == null
                    ? ""
                    : response.body().substring(0, Math.min(240, response.body().length()));
            LOG.warn("{} HTTP {} model={} body={}", label, response.statusCode(), model, snippet);
            throw new IllegalStateException(label + " HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        String content = extractContent(root);
        if (content.isBlank()) {
            LOG.warn("{} empty content model={} finish={}",
                    label, model, root.path("choices").path(0).path("finish_reason").asText());
            throw new IllegalStateException(label + " вернула пустой ответ");
        }
        return content.trim();
    }

    private boolean ollamaEnabled() {
        return ollama == null || !Boolean.FALSE.equals(ollama.enabled());
    }

    private String ollamaBase() {
        String url = ollama == null || ollama.baseUrl() == null || ollama.baseUrl().isBlank()
                ? "http://localhost:11434/v1"
                : ollama.baseUrl().trim();
        return url.replaceAll("/$", "");
    }

    private String ollamaKey() {
        if (ollama == null || ollama.apiKey() == null || ollama.apiKey().isBlank()) {
            // локальный Ollama ключ не требует; cloud - нужен OLLAMA_API_KEY
            return "";
        }
        return ollama.apiKey().trim();
    }

    private String ollamaModel(boolean vision) {
        if (vision) {
            return pick(ollama == null ? null : ollama.vision(), "llava");
        }
        return pick(ollama == null ? null : ollama.chat(), "llama3.2");
    }

    /** Для fast/critic можно звать отдельно, но fallback всегда на chat/fast из конфига. */
    String mapToOllama(String requested) {
        if (requested == null) {
            return ollamaModel(false);
        }
        String lower = requested.toLowerCase(Locale.ROOT);
        if (lower.contains("vision") || lower.contains("llava")) {
            return ollamaModel(true);
        }
        if (lower.contains("flash") || lower.contains("fast") || lower.contains("critic") || lower.contains("glm")) {
            return pick(ollama == null ? null : ollama.fast(), ollamaModel(false));
        }
        return ollamaModel(false);
    }

    private static String pick(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String extractContent(JsonNode root) {
        JsonNode message = root.path("choices").path(0).path("message");
        String content = textOf(message.path("content"));
        if (!content.isBlank()) {
            return content;
        }
        String text = textOf(root.path("choices").path(0).path("text"));
        if (!text.isBlank()) {
            return text;
        }
        String reasoning = textOf(message.path("reasoning_content"));
        if (!reasoning.isBlank()) {
            return reasoning;
        }
        // Ollama Cloud reasoning models sometimes put text only in "reasoning"
        return textOf(message.path("reasoning"));
    }

    static String textOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        if (node.isArray()) {
            StringBuilder out = new StringBuilder();
            for (JsonNode part : node) {
                if (part.isTextual()) {
                    out.append(part.asText(""));
                } else {
                    String piece = part.path("text").asText("");
                    if (piece.isBlank()) {
                        piece = part.path("content").asText("");
                    }
                    out.append(piece);
                }
            }
            return out.toString();
        }
        return node.asText("");
    }

    public record Turn(String role, String content) {
    }

    public record StatusView(
            String primary,
            String ollamaBaseUrl,
            boolean ollamaEnabled,
            String ollamaModel,
            String lastProvider,
            String lastError
    ) {
    }
}
