package ru.infereco.demo.barista.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
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
    private volatile String botUsername = "";

    public TelegramNotifier(MeetProperties properties) {
        this.properties = properties;
        if (configuredChat() != null) {
            chats.add(configuredChat());
        }
        loadSaved();
    }

    @PostConstruct
    void start() {
        Thread.startVirtualThread(this::bootstrapAndLoop);
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
            hint = "бот @" + (botUsername.isBlank() ? "assistentPTObot" : botUsername)
                    + " жив. Напишите ему /start в Telegram.";
        } else {
            hint = "ок: @" + (botUsername.isBlank() ? "?" : botUsername) + ", чатов " + chats.size();
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
            LOG.info("telegram chat {}", chatId);
        }
    }

    public void sendAll(String text) {
        if (!enabled() || text == null || text.isBlank()) {
            return;
        }
        Set<String> targets;
        synchronized (this) {
            targets = Set.copyOf(chats);
        }
        if (targets.isEmpty()) {
            lastError = "нет подписчиков: напишите /start боту";
            LOG.warn("telegram: некому слать notice");
            return;
        }
        for (String chatId : targets) {
            try {
                send(chatId, text);
                lastError = "";
            } catch (Exception ex) {
                lastError = "send " + chatId + ": " + ex.getMessage();
                LOG.warn("telegram send {}: {}", chatId, ex.getMessage());
            }
        }
    }

    public void pollOnce() {
        if (!enabled()) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + token()
                    + "/getUpdates?timeout=25&offset=" + offset;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(35))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                lastError = "getUpdates HTTP " + response.statusCode();
                return;
            }
            JsonNode root = mapper.readTree(response.body());
            if (!root.path("ok").asBoolean(false)) {
                lastError = "getUpdates: " + root.path("description").asText("not ok");
                return;
            }
            lastError = "";
            for (JsonNode update : root.path("result")) {
                long updateId = update.path("update_id").asLong();
                offset = Math.max(offset, updateId + 1);
                JsonNode message = update.path("message");
                if (message.isMissingNode()) {
                    message = update.path("edited_message");
                }
                String chatId = message.path("chat").path("id").asText("");
                String text = message.path("text").asText("");
                if (chatId.isBlank()) {
                    continue;
                }
                remember(chatId);
                if (text != null && text.toLowerCase().contains("/start")) {
                    send(chatId, """
                            TopSP на связи.
                            Пишите /start, чтобы получать notice по изменениям СП.
                            Когда загрузите новую версию постановки, сюда придёт summary и ссылка на задачу.
                            """);
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            lastError = ex.getMessage() == null ? "poll failed" : ex.getMessage();
            LOG.warn("telegram poll: {}", lastError);
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void bootstrapAndLoop() {
        if (!enabled()) {
            LOG.info("telegram выключен: нет токена");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.telegram.org/bot" + token() + "/getMe"))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            if (root.path("ok").asBoolean(false)) {
                botUsername = root.path("result").path("username").asText("");
                lastError = "";
                LOG.info("telegram бот @{} готов", botUsername);
            } else {
                lastError = "getMe: " + root.path("description").asText("fail");
                LOG.warn("telegram getMe: {}", lastError);
            }
        } catch (Exception ex) {
            lastError = "getMe: " + ex.getMessage();
            LOG.warn("telegram getMe: {}", lastError);
        }
        while (!Thread.currentThread().isInterrupted()) {
            if (!enabled()) {
                try {
                    Thread.sleep(15_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            pollOnce();
        }
    }

    private void send(String chatId, String text) throws Exception {
        String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(trim(text), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token() + "/sendMessage"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + response.body());
        }
        JsonNode root = mapper.readTree(response.body());
        if (!root.path("ok").asBoolean(false)) {
            throw new IllegalStateException(root.path("description").asText("send failed"));
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
        if (text == null) {
            return "";
        }
        return text.length() > 3500 ? text.substring(0, 3500) + "…" : text;
    }
}
