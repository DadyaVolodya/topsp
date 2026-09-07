package ru.infereco.demo.barista.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MetricsRegistry {

    private final AtomicInteger turns = new AtomicInteger();
    private final AtomicInteger hints = new AtomicInteger();
    private final AtomicInteger screens = new AtomicInteger();
    private final AtomicInteger improvementCycles = new AtomicInteger();
    private final AtomicInteger revisions = new AtomicInteger();
    private final AtomicInteger knowledgeDocs = new AtomicInteger();
    private final AtomicInteger pendingImprove = new AtomicInteger();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private volatile String lastFact = "";
    private volatile String lastReason = "";
    private volatile boolean lastLearned;
    private volatile long specDiffDurationMs;
    private volatile long impactAnalysisDurationMs;
    private volatile long taskGenerationDurationMs;
    private volatile int changesDetected;
    private volatile int highImpactChanges;
    private volatile int affectedFiles;
    private volatile int tasksGenerated;
    private final List<MetricsPoint> history = new ArrayList<>();

    public synchronized void recordCopilot(
            long specDiffMs,
            long impactMs,
            long taskMs,
            int changes,
            int highImpact,
            int files,
            int tasks
    ) {
        specDiffDurationMs = specDiffMs;
        impactAnalysisDurationMs = impactMs;
        taskGenerationDurationMs = taskMs;
        changesDetected = changes;
        highImpactChanges = highImpact;
        affectedFiles = files;
        tasksGenerated += tasks;
    }

    public synchronized void recordTurn(long latencyMs) {
        turns.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        snapshot(latencyMs);
    }

    public synchronized void recordHint(long latencyMs) {
        hints.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
    }

    public synchronized void recordScreen(long latencyMs) {
        screens.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
    }

    public void beginImprove() {
        pendingImprove.incrementAndGet();
    }

    public void endImprove() {
        pendingImprove.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void recordRevision() {
        revisions.incrementAndGet();
    }

    public void setKnowledgeDocs(int count) {
        knowledgeDocs.set(count);
    }

    public synchronized void recordImprovement(double quality, boolean learned, String fact, String reason) {
        improvementCycles.incrementAndGet();
        lastLearned = learned;
        lastFact = fact == null ? "" : fact;
        lastReason = reason == null ? "" : reason;
        history.add(new MetricsPoint(Instant.now(), quality, knowledgeDocs.get(), 0));
    }

    public MetricsSnapshot snapshotView() {
        int turnCount = turns.get();
        long avg = turnCount == 0 ? 0 : totalLatencyMs.get() / Math.max(turnCount + hints.get() + screens.get(), 1);
        double lastQuality = history.isEmpty() ? 0 : history.getLast().quality();
        return new MetricsSnapshot(
                turnCount,
                hints.get(),
                screens.get(),
                improvementCycles.get(),
                revisions.get(),
                knowledgeDocs.get(),
                pendingImprove.get(),
                avg,
                lastQuality,
                lastLearned,
                lastFact,
                lastReason,
                specDiffDurationMs,
                impactAnalysisDurationMs,
                taskGenerationDurationMs,
                changesDetected,
                highImpactChanges,
                affectedFiles,
                tasksGenerated
        );
    }

    public synchronized List<MetricsPoint> history() {
        return List.copyOf(history);
    }

    private void snapshot(long latencyMs) {
        double quality = history.isEmpty() ? 0 : history.getLast().quality();
        history.add(new MetricsPoint(Instant.now(), quality, knowledgeDocs.get(), latencyMs));
    }

    public record MetricsSnapshot(
            int turns,
            int hints,
            int screens,
            int improvementCycles,
            int revisions,
            int knowledgeDocs,
            int pendingImprove,
            long avgLatencyMs,
            double lastQuality,
            boolean lastLearned,
            String lastFact,
            String lastReason,
            long specDiffDurationMs,
            long impactAnalysisDurationMs,
            long taskGenerationDurationMs,
            int changesDetected,
            int highImpactChanges,
            int affectedFiles,
            int tasksGenerated
    ) {
    }

    public record MetricsPoint(Instant at, double quality, int knowledgeDocs, long latencyMs) {
    }
}
