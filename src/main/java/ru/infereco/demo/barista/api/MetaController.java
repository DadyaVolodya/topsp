package ru.infereco.demo.barista.api;

import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.metrics.MetricsRegistry;
import ru.infereco.demo.barista.skill.SkillCatalog;
import ru.infereco.demo.barista.stt.VoskSttService;

@RestController
@RequestMapping("/api")
public class MetaController {

    private final MetricsRegistry metrics;
    private final KnowledgeCatalog knowledge;
    private final MeetProperties properties;
    private final VoskSttService stt;
    private final SkillCatalog skills;

    public MetaController(
            MetricsRegistry metrics,
            KnowledgeCatalog knowledge,
            MeetProperties properties,
            VoskSttService stt,
            SkillCatalog skills
    ) {
        this.metrics = metrics;
        this.knowledge = knowledge;
        this.properties = properties;
        this.stt = stt;
        this.skills = skills;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    @GetMapping("/models")
    public ApiDtos.ModelsResponse models() {
        return new ApiDtos.ModelsResponse(
                properties.models().chat(),
                properties.models().critic(),
                properties.models().fast(),
                properties.models().vision(),
                properties.topic()
        );
    }

    @GetMapping("/voice")
    public ApiDtos.VoiceSettingsResponse voice() {
        return new ApiDtos.VoiceSettingsResponse(
                properties.voice().pauseMs(),
                properties.voice().maxUtteranceMs(),
                properties.voice().hintIdleMs()
        );
    }

    @GetMapping("/tuning")
    public ApiDtos.TuningResponse tuning() {
        return new ApiDtos.TuningResponse(
                properties.topic(),
                properties.models().chat(),
                properties.models().critic(),
                properties.models().fast(),
                properties.speed().temperature(),
                properties.speed().chatMaxTokens(),
                properties.speed().hintMaxTokens(),
                properties.speed().historyMessages(),
                properties.style().maxSentences(),
                properties.style().allowMarkdown(),
                properties.voice().hintIdleMs(),
                properties.improve().maxRevisions(),
                properties.fullSystemPrompt()
        );
    }

    @GetMapping("/metrics")
    public MetricsRegistry.MetricsSnapshot metrics() {
        return metrics.snapshotView();
    }

    @GetMapping("/metrics/history")
    public List<MetricsRegistry.MetricsPoint> history() {
        return metrics.history();
    }

    @GetMapping("/knowledge")
    public List<KnowledgeGroupView> knowledge() {
        return knowledge.groups().stream()
                .map(group -> new KnowledgeGroupView(group.title(), group.source(), group.chunks(), group.chars()))
                .toList();
    }

    @GetMapping("/stt")
    public VoskSttService.StatusView stt() {
        return stt.snapshot();
    }

    @GetMapping("/skills")
    public List<SkillView> skills() {
        return skills.all().stream()
                .map(skill -> new SkillView(skill.id(), skill.title(), skill.triggers()))
                .toList();
    }

    @PostMapping(path = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptResponse transcribe(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("audio is required");
        }
        String text = stt.transcribe(file.getBytes());
        return new TranscriptResponse(text == null ? "" : text);
    }

    @PostMapping(path = "/knowledge/txt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<KnowledgeView> uploadTxt(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        return knowledge.ingestTxt(file.getOriginalFilename(), file.getBytes()).stream()
                .map(doc -> new KnowledgeView(doc.id(), doc.title(), doc.source(), doc.body().length()))
                .toList();
    }

    public record TranscriptResponse(String text) {
    }

    public record HealthResponse(String status) {
    }

    public record KnowledgeView(String id, String title, String source, int chars) {
    }

    public record KnowledgeGroupView(String title, String source, int chunks, int chars) {
    }

    public record SkillView(String id, String title, List<String> triggers) {
    }
}
