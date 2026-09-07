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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InferecoClient {

    private static final Logger LOG = LoggerFactory.getLogger(InferecoClient.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;

    public InferecoClient(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.base-url}") String baseUrl
    ) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.baseUrl = baseUrl == null ? "https://api.infereco.ru/v1" : baseUrl.replaceAll("/$", "");
    }

    public String complete(String model, String system, List<Turn> history, String user, int maxTokens, double temperature) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
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
            return send(body, model, Duration.ofSeconds(14));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Infereco прервана", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "Infereco failed" : ex.getMessage(), ex);
        }
    }

    public String completeVision(String model, String system, String user, String dataUrl, int maxTokens, double temperature) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
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
            return send(body, model, Duration.ofSeconds(20));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Infereco прервана", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "Infereco vision failed" : ex.getMessage(), ex);
        }
    }

    private String send(ObjectNode body, String model, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            String snippet = response.body() == null ? "" : response.body().substring(0, Math.min(240, response.body().length()));
            LOG.warn("Infereco HTTP {} model={} body={}", response.statusCode(), model, snippet);
            throw new IllegalStateException("Infereco HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        String content = extractContent(root);
        if (content.isBlank()) {
            LOG.warn("Infereco empty content model={} finish={}", model, root.path("choices").path(0).path("finish_reason").asText());
            throw new IllegalStateException("Infereco вернула пустой ответ");
        }
        return content.trim();
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
        return textOf(message.path("reasoning_content"));
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
}
