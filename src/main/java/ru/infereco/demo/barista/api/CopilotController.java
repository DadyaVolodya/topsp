package ru.infereco.demo.barista.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.infereco.demo.barista.code.CodeChunk;
import ru.infereco.demo.barista.code.CodeLibrary;
import ru.infereco.demo.barista.spec.NavigationHints;
import ru.infereco.demo.barista.spec.RequirementCodeLink;
import ru.infereco.demo.barista.spec.SpecChange;
import ru.infereco.demo.barista.spec.SpecDiffService;
import ru.infereco.demo.barista.spec.SpecPipeline;
import ru.infereco.demo.barista.spec.SpecSection;
import ru.infereco.demo.barista.spec.SpecVersionSnap;
import ru.infereco.demo.barista.config.MeetProperties;

@RestController
@RequestMapping("/api")
public class CopilotController {

    private final SpecPipeline pipeline;
    private final CodeLibrary code;
    private final MeetProperties properties;

    public CopilotController(SpecPipeline pipeline, CodeLibrary code, MeetProperties properties) {
        this.pipeline = pipeline;
        this.code = code;
        this.properties = properties;
    }

    @GetMapping("/copilot/overview")
    public SpecPipeline.Overview overview(@RequestParam(required = false) String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return pipeline.overview();
        }
        return pipeline.overview(documentId);
    }

    @PostMapping("/copilot/demo/seed")
    public SpecPipeline.Overview seedDemo() {
        return pipeline.seedDemo();
    }

    @GetMapping("/specs/{documentId}/versions")
    public VersionsResponse versions(@PathVariable String documentId) {
        List<SpecVersionSnap> snaps = pipeline.versions(documentId);
        if (snaps.isEmpty()) {
            throw new IllegalArgumentException("нет версий для " + documentId);
        }
        return new VersionsResponse(
                documentId,
                snaps.getLast().fileName(),
                snaps.stream().map(item -> VersionView.from(item, 600)).toList());
    }

    @GetMapping("/specs/{documentId}/changes")
    public ChangesResponse changes(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "false") boolean includeUnchanged
    ) {
        List<SpecChange> items = pipeline.changes(documentId, includeUnchanged);
        SpecVersionSnap latest = pipeline.versions(documentId).isEmpty()
                ? null
                : pipeline.versions(documentId).getLast();
        int from = items.stream().mapToInt(SpecChange::fromVersion).min()
                .orElse(0);
        int to = items.stream().mapToInt(SpecChange::toVersion).max()
                .orElse(latest == null ? 0 : latest.version());
        return new ChangesResponse(
                latest == null ? documentId : latest.fileName(),
                documentId,
                from,
                to,
                SpecDiffService.summary(items),
                items.stream().map(item -> ChangeView.from(item, false)).toList());
    }

    @GetMapping("/specs/{documentId}/changes/{changeId}")
    public ChangeView change(@PathVariable String documentId, @PathVariable String changeId) {
        SpecChange change = pipeline.change(documentId, changeId);
        if (change == null) {
            throw new IllegalArgumentException("изменение не найдено");
        }
        return ChangeView.from(change, true);
    }

    @PostMapping("/specs/{documentId}/reanalyze")
    public ChangesResponse reanalyze(@PathVariable String documentId) {
        if (pipeline.versions(documentId).isEmpty()) {
            throw new IllegalArgumentException("нет версий для " + documentId);
        }
        pipeline.analyze(documentId, true);
        return changes(documentId, false);
    }

    @PostMapping("/specs/{documentId}/changes/{changeId}/exclude")
    public ChangeView exclude(
            @PathVariable String documentId,
            @PathVariable String changeId,
            @RequestBody(required = false) ExcludeRequest body
    ) {
        String path = body == null ? null : body.path();
        String symbol = body == null ? null : body.symbol();
        SpecChange change = pipeline.exclude(documentId, changeId, path, symbol);
        if (change == null) {
            throw new IllegalArgumentException("изменение не найдено");
        }
        return ChangeView.from(change, true);
    }

    @PostMapping("/specs/{documentId}/changes/{changeId}/include")
    public ChangeView include(@PathVariable String documentId, @PathVariable String changeId) {
        SpecChange change = pipeline.include(documentId, changeId);
        if (change == null) {
            throw new IllegalArgumentException("изменение не найдено");
        }
        return ChangeView.from(change, true);
    }

    @GetMapping("/specs/{documentId}/links")
    public List<RequirementCodeLink> links(@PathVariable String documentId) {
        return pipeline.links(documentId);
    }

    @GetMapping("/code/symbols")
    public List<CodeChunk> symbols(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String side,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return code.search(q, side, Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/navigation")
    public SpecChange.NavigationHint navigation(@RequestParam("q") String query) {
        String url = properties.demo() == null || properties.demo().pto() == null
                ? "https://dev.aisto.local"
                : properties.demo().pto().url();
        return NavigationHints.from("", query, url);
    }

    public record ExcludeRequest(String path, String symbol) {
    }

    public record VersionsResponse(String documentId, String fileName, List<VersionView> versions) {
    }

    public record VersionView(
            String documentId,
            String fileName,
            int version,
            long fingerprint,
            Instant createdAt,
            int chars,
            List<SectionView> sections
    ) {
        static VersionView from(SpecVersionSnap snap, int clip) {
            return new VersionView(
                    snap.documentId(),
                    snap.fileName(),
                    snap.version(),
                    snap.fingerprint(),
                    snap.createdInstant(),
                    snap.rawText() == null ? 0 : snap.rawText().length(),
                    snap.sections() == null ? List.of() : snap.sections().stream()
                            .map(section -> SectionView.from(section, clip))
                            .toList());
        }
    }

    public record SectionView(String sectionPath, String heading, String text, String hash) {
        static SectionView from(SpecSection section, int clip) {
            return new SectionView(section.sectionPath(), section.heading(), clip(section.text(), clip), section.hash());
        }
    }

    public record ChangesResponse(
            String document,
            String documentId,
            int fromVersion,
            int toVersion,
            Map<String, Integer> summary,
            List<ChangeView> changes
    ) {
    }

    public record ChangeView(
            String id,
            String documentId,
            int fromVersion,
            int toVersion,
            String type,
            String significance,
            String sectionPath,
            String heading,
            String summary,
            String oldBehavior,
            String newBehavior,
            String businessImpact,
            boolean requiresCodeChange,
            String oldText,
            String newText,
            boolean excluded,
            List<SpecChange.AffectedCode> affected,
            SpecChange.NavigationHint navigation
    ) {
        static ChangeView from(SpecChange change, boolean full) {
            int clip = full ? 4000 : 800;
            return new ChangeView(
                    change.id(),
                    change.documentId(),
                    change.fromVersion(),
                    change.toVersion(),
                    change.type(),
                    change.significance(),
                    change.sectionPath(),
                    change.heading(),
                    change.summary(),
                    change.oldBehavior(),
                    change.newBehavior(),
                    change.businessImpact(),
                    change.requiresCodeChange(),
                    change.oldText(),
                    change.newText(),
                    change.excluded(),
                    change.affected() == null ? List.of() : change.affected(),
                    change.navigation());
        }
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
