package ru.infereco.demo.barista.api;

import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping("/tasks")
    public List<DevTaskWriter.TaskView> tasks() {
        return tasks.recent(20);
    }

    @GetMapping("/telegram")
    public TelegramNotifier.StatusView telegram() {
        return telegram.status();
    }

    @GetMapping("/code")
    public CodeResponse code() {
        return new CodeResponse(code.files(), code.chunks(), code.symbols(), code.lastChange(), code.list());
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
            Instant lastChange,
            List<CodeLibrary.CodeView> roots
    ) {
    }
}
