package ru.infereco.demo.barista.improve;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.infereco.demo.barista.chat.InferecoClient;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog.KnowledgeDoc;
import ru.infereco.demo.barista.metrics.MetricsRegistry;

@Service
public class SelfImproveService {

    private static final Logger LOG = LoggerFactory.getLogger(SelfImproveService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");

    private final InferecoClient infereco;
    private final MeetProperties properties;
    private final KnowledgeCatalog knowledge;
    private final MetricsRegistry metrics;

    public SelfImproveService(
            InferecoClient infereco,
            MeetProperties properties,
            KnowledgeCatalog knowledge,
            MetricsRegistry metrics
    ) {
        this.infereco = infereco;
        this.properties = properties;
        this.knowledge = knowledge;
        this.metrics = metrics;
    }

    @Async
    public void afterTurn(String userText, String answer, List<KnowledgeDoc> retrieved, boolean doom) {
        metrics.beginImprove();
        try {
            Critique critique = critique(userText, answer, retrieved, doom);
            learn(critique);
        } catch (RuntimeException ex) {
            metrics.recordImprovement(0, false, "", ex.getMessage() == null ? "critic failed" : ex.getMessage());
        } finally {
            metrics.endImprove();
        }
    }

    public Critique critique(String userText, String answer, List<KnowledgeDoc> retrieved, boolean doom) {
        String who = doom ? "Doom-рации" : "помощника совещания";
        String extra = doom
                ? "keep=true только если fact конкретный про этого игрока или карту и его не было в базе."
                : "keep=true только если fact конкретный и его не было в базе.";
        String prompt = """
                Ты критик %s. Оцени ответ и реши, стоит ли выучить новый факт.
                Верни ТОЛЬКО JSON без markdown:
                {"quality":0-10,"keep":true|false,"revise":true|false,"title":"краткий заголовок","fact":"один новый факт или пусто","reason":"почему такая оценка"}
                %s
                revise=true если ответ слабо помогает.

                Реплика:
                """.formatted(who, extra) + userText + """

                Ответ:
                """ + answer + """

                База:
                """ + knowledge.formatForPrompt(retrieved);
        String raw = infereco.complete(
                properties.models().critic(),
                "Верни только JSON. Первый символ { последний }. Без рассуждений.",
                List.of(),
                prompt,
                1200,
                0.1);
        Critique parsed = Critique.parse(raw);
        if (parsed.quality() == 0) {
            LOG.warn("critic parse miss: {}", raw == null ? "" : raw.substring(0, Math.min(400, raw.length())));
        }
        return parsed;
    }

    public String revise(String userText, String draft, Critique critique, String ragContext) {
        String prompt = """
                Перепиши ответ. Учти критику. Не извиняйся.

                Критика:
                """ + critique.reason() + """

                Черновик:
                """ + draft + "\n" + ragContext + """

                Реплика ведущего:
                """ + userText;
        String content = infereco.complete(
                properties.models().chat(),
                "Перепиши ответ. Учти критику. Не извиняйся.",
                List.of(),
                prompt,
                properties.speed().chatMaxTokens(),
                0.3);
        return content == null || content.isBlank() ? draft : content;
    }

    public boolean learn(Critique critique) {
        if (!critique.keep() || critique.fact().isBlank()) {
            metrics.recordImprovement(critique.quality(), false, critique.fact(), critique.reason());
            return false;
        }
        knowledge.learn(critique.title().isBlank() ? "learned fact" : critique.title(), critique.fact());
        metrics.recordImprovement(critique.quality(), true, critique.fact(), critique.reason());
        return true;
    }

    public record Critique(double quality, boolean keep, boolean revise, String title, String fact, String reason) {
        public static Critique parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new Critique(0, false, false, "", "", "пустой ответ критика");
            }
            Matcher block = JSON_BLOCK.matcher(raw);
            String json = block.find() ? block.group() : raw;
            return new Critique(
                    number(json, "quality"),
                    bool(json, "keep"),
                    bool(json, "revise"),
                    text(json, "title"),
                    text(json, "fact"),
                    text(json, "reason")
            );
        }

        private static double number(String json, String key) {
            Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?").matcher(json);
            return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
        }

        private static boolean bool(String json, String key) {
            Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(json);
            return matcher.find() && Boolean.parseBoolean(matcher.group(1));
        }

        private static String text(String json, String key) {
            Matcher matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            return matcher.find() ? matcher.group(1).trim() : "";
        }
    }
}
