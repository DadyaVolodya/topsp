package ru.infereco.demo.barista.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.infereco.demo.barista.code.CodeChunk;
import ru.infereco.demo.barista.code.CodeLibrary;
import ru.infereco.demo.barista.spec.DevTaskWriter;
import ru.infereco.demo.barista.spec.SpecLibrary;
import ru.infereco.demo.barista.spec.SpecPipeline;
import ru.infereco.demo.barista.spec.SpecSectionParser;
import ru.infereco.demo.barista.spec.TelegramNotifier;

@RestController
@RequestMapping("/api")
public class SpecController {

    private final SpecLibrary specs;
    private final DevTaskWriter tasks;
    private final CodeLibrary code;
    private final TelegramNotifier telegram;
    private final SpecPipeline pipeline;

    public SpecController(
            SpecLibrary specs,
            DevTaskWriter tasks,
            CodeLibrary code,
            TelegramNotifier telegram,
            SpecPipeline pipeline
    ) {
        this.specs = specs;
        this.tasks = tasks;
        this.code = code;
        this.telegram = telegram;
        this.pipeline = pipeline;
    }

    @GetMapping("/specs")
    public SpecsResponse specs() {
        List<SpecItem> files = specs.list().stream().map(item -> {
            String id = SpecSectionParser.documentId(item.file());
            var overview = pipeline.overview(id);
            return new SpecItem(
                    item.file(),
                    id,
                    item.bytes(),
                    item.fingerprint(),
                    overview.toVersion(),
                    overview.summary() == null ? 0 : overview.summary().getOrDefault("total", 0));
        }).toList();
        return new SpecsResponse(specs.dir().toString(), specs.specChunks(), specs.lastChange(), files);
    }

    @PostMapping(path = "/specs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpecPipeline.Overview uploadSpec(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("нужен файл СП (.md/.txt)");
        }
        String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "upload.md"
                : file.getOriginalFilename().trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".doc"))) {
            throw new IllegalArgumentException("поддерживаются .md, .txt, .doc");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        String active = "petclinic-visits.md";
        specs.ingest(active, text, true);
        return pipeline.overview(SpecSectionParser.documentId(active));
    }

    @PostMapping(path = "/specs/pair", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpecPipeline.Overview uploadPair(
            @RequestParam("oldFile") MultipartFile oldFile,
            @RequestParam("newFile") MultipartFile newFile
    ) throws IOException {
        if (oldFile == null || oldFile.isEmpty() || newFile == null || newFile.isEmpty()) {
            throw new IllegalArgumentException("нужны оба файла: старое СП и новое СП");
        }
        String oldText = new String(oldFile.getBytes(), StandardCharsets.UTF_8);
        String newText = new String(newFile.getBytes(), StandardCharsets.UTF_8);
        if (oldText.isBlank() || newText.isBlank()) {
            throw new IllegalArgumentException("файлы СП пустые");
        }
        String active = "petclinic-visits.md";
        SpecPipeline.Overview overview = pipeline.loadPair(active, oldText, newText);
        specs.indexOnly(active, newText);
        return overview;
    }

    @GetMapping("/tasks")
    public List<DevTaskWriter.TaskView> tasks() {
        return tasks.recent(20);
    }

    @GetMapping("/telegram")
    public TelegramNotifier.StatusView telegram() {
        return telegram.status();
    }

    @GetMapping("/code")
    public CodeResponse code(@RequestParam(defaultValue = "40") int limit) {
        List<CodeChunk> items = code.preview(limit);
        return new CodeResponse(
                code.files(),
                code.chunks(),
                code.symbols(),
                items.size(),
                code.lastChange(),
                code.list(),
                items);
    }

    public record SpecsResponse(
            String dir,
            long chunks,
            Instant lastChange,
            List<SpecItem> files
    ) {
    }

    public record SpecItem(
            String file,
            String documentId,
            long bytes,
            long fingerprint,
            int version,
            int changes
    ) {
    }

    public record CodeResponse(
            int files,
            long chunks,
            int symbols,
            int shown,
            Instant lastChange,
            List<CodeLibrary.CodeView> roots,
            List<CodeChunk> items
    ) {
    }
}
