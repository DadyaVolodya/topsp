package ru.infereco.demo.barista.api;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.infereco.demo.barista.chat.ChatService;
import ru.infereco.demo.barista.config.MeetProperties;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final ChatService chatService;
    private final MeetProperties properties;

    public SessionController(ChatService chatService, MeetProperties properties) {
        this.chatService = chatService;
        this.properties = properties;
    }

    @PostMapping
    public ApiDtos.CreateSessionResponse create() {
        return ApiDtos.CreateSessionResponse.from(chatService.start(), properties.topic());
    }

    @GetMapping("/{id}")
    public ApiDtos.SessionResponse get(@PathVariable UUID id) {
        return ApiDtos.SessionResponse.from(chatService.get(id));
    }

    @PostMapping("/{id}/messages")
    public ApiDtos.MessageResponse message(@PathVariable UUID id, @Valid @RequestBody ApiDtos.MessageRequest request) {
        return ApiDtos.MessageResponse.from(
                chatService.reply(id, request.text(), request.source()),
                chatService.get(id).skill());
    }

    @PostMapping("/{id}/doom")
    public ApiDtos.CreateSessionResponse doom(@PathVariable UUID id) {
        return ApiDtos.CreateSessionResponse.from(chatService.enterDoom(id), properties.doomTopic());
    }

    @PostMapping("/{id}/screen")
    public ApiDtos.MessageResponse screen(@PathVariable UUID id, @RequestBody ApiDtos.ScreenRequest request) {
        if (request == null || request.image() == null || request.image().isBlank()) {
            throw new IllegalArgumentException("нужен кадр экрана");
        }
        return ApiDtos.MessageResponse.from(chatService.watchScreen(id, request.image()), chatService.get(id).skill());
    }

    @PostMapping("/{id}/hints")
    public ApiDtos.MessageResponse hint(@PathVariable UUID id, @RequestBody(required = false) ApiDtos.HintRequest request) {
        int elapsed = request == null || request.elapsedSec() == null ? 0 : Math.max(request.elapsedSec(), 0);
        String cheat = request == null || request.lastCheat() == null || request.lastCheat().isBlank()
                ? "нет"
                : request.lastCheat().trim();
        boolean playing = request != null && Boolean.TRUE.equals(request.playing());
        return ApiDtos.MessageResponse.from(chatService.hint(id, elapsed, cheat, playing), chatService.get(id).skill());
    }
}
