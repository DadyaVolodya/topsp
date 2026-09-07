package ru.infereco.demo.barista.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class TelegramNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramNotifier.class);
    private static final Path CHATS_FILE = Path.of("config/telegram-chats.txt");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MeetProperties properties;
    private final Set<String> chats = new LinkedHashSet<>();
    private volatile long offset = 0;
    private volatile String lastError = "";

    public TelegramNotifier(MeetProperties properties) {
        this.properties = properties;
        if (configuredChat() != null) {
            chats.add(configuredChat());
        }
        loadSaved();
    }

    public boolean enabled() {
        return token() != null;
    }

    public StatusView status() {
        String hint;
        if (!enabled()) {
            hint = "нет токена в config/application-local.yml";
        } else if (!lastError.isBlank()) {
            hint = lastError;
        } else if (chats.isEmpty()) {
            hint = "telegram не используется";
        } else {
            hint = "telegram не используется";
        }
        return new StatusView(enabled(), chats.size(), hint);
    }

    public record StatusView(boolean enabled, int chats, String hint) {
    }

    public synchronized void remember(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            return;
        }
        if (chats.add(chatId.trim())) {
            persist();
        }
    }

    public void sendAll(String text) {
        // сеть Telegram не используем: summary остаётся в lastNotice пайплайна
    }

    public void pollOnce() {
    }

    private void send(String chatId, String text) throws Exception {
        String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(trim(text), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token() + "/sendMessage"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
    }

    private String token() {
        MeetProperties.Telegram telegram = properties.telegram();
        if (telegram == null || telegram.token() == null || telegram.token().isBlank()) {
            return null;
        }
        return telegram.token().trim();
    }

    private String configuredChat() {
        MeetProperties.Telegram telegram = properties.telegram();
        if (telegram == null || telegram.chatId() == null || telegram.chatId().isBlank()) {
            return null;
        }
        return telegram.chatId().trim();
    }

    private void loadSaved() {
        try {
            if (!Files.isRegularFile(CHATS_FILE)) {
                return;
            }
            for (String line : Files.readAllLines(CHATS_FILE, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    chats.add(line.trim());
                }
            }
        } catch (Exception ex) {
            LOG.warn("не прочитал telegram chats: {}", ex.getMessage());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(CHATS_FILE.getParent());
            Files.write(CHATS_FILE, chats, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOG.warn("не записал telegram chats: {}", ex.getMessage());
        }
    }

    private static String trim(String text) {
        return text.length() > 3500 ? text.substring(0, 3500) + "…" : text;
    }
}
