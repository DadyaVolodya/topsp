package ru.infereco.demo.barista.improve;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.chat.InferecoClient;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog.KnowledgeDoc;
import ru.infereco.demo.barista.metrics.MetricsRegistry;
import ru.infereco.demo.barista.spec.ProductBrief;
import ru.infereco.demo.barista.spec.SpecPipeline;

/**
 * Фоновый цикл: запросы в Infereco по PetClinic + критика/обучение.
 */
@Component
public class ContinuousLearnService {

    private static final Logger LOG = LoggerFactory.getLogger(ContinuousLearnService.class);

    private static final String[] DRILLS = {
            "Кратко: что такое Visit в PetClinic и где он в коде?",
            "По текущей СП: когда можно отменить визит (canCancel)?",
            "Какой frontend нужно менять, если разрешили правку description?",
            "Какой backend реализует правила canCancel и canEditDescription?",
            "Сравни было/стало для раздела 3.2 и назови затронутые символы."
    };

    private final MeetProperties properties;
    private final InferecoClient infereco;
    private final KnowledgeCatalog knowledge;
    private final SelfImproveService selfImprove;
    private final ProductBrief productBrief;
    private final SpecPipeline pipeline;
    private final MetricsRegistry metrics;
    private final AtomicInteger tick = new AtomicInteger();

    public ContinuousLearnService(
            MeetProperties properties,
            InferecoClient infereco,
            KnowledgeCatalog knowledge,
            SelfImproveService selfImprove,
            ProductBrief productBrief,
            SpecPipeline pipeline,
            MetricsRegistry metrics
    ) {
        this.properties = properties;
        this.infereco = infereco;
        this.knowledge = knowledge;
        this.selfImprove = selfImprove;
        this.productBrief = productBrief;
        this.pipeline = pipeline;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelay = 50_000, initialDelay = 20_000)
    public void drill() {
        MeetProperties.Improve improve = properties.improve();
        if (improve != null && !improve.loopOn()) {
            return;
        }
        if (pipeline.primaryDocumentId() == null || pipeline.versions(pipeline.primaryDocumentId()).isEmpty()) {
            return;
        }
        int n = tick.getAndIncrement();
        String question = DRILLS[n % DRILLS.length];
        long started = System.currentTimeMillis();
        try {
            List<KnowledgeDoc> retrieved = knowledge.retrieve(question + " VisitService VisitRow petclinic", 5);
            String rag = productBrief.forTopSp(question) + "\n" + knowledge.formatForPrompt(retrieved);
            String answer = infereco.complete(
                    properties.models().chat(),
                    properties.fullSystemPrompt() + "\nТы в режиме непрерывного обучения TopSP/PetClinic.",
                    List.of(),
                    rag + "\nРеплика:\n" + question,
                    properties.speed().chatMaxTokens(),
                    properties.speed().temperature());
            long latency = System.currentTimeMillis() - started;
            metrics.recordTurn(latency);
            selfImprove.afterTurn(question, answer, retrieved, false);
            LOG.info("learn-loop #{} {}ms q={}", n, latency, question);
        } catch (Exception ex) {
            LOG.warn("learn-loop #{}: {}", n, ex.getMessage());
            metrics.recordImprovement(0, false, "", ex.getMessage() == null ? "learn failed" : ex.getMessage());
        }
    }
}
