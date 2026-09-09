package ru.infereco.demo.barista.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.improve.SelfImproveService;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog.KnowledgeDoc;
import ru.infereco.demo.barista.metrics.MetricsRegistry;
import ru.infereco.demo.barista.skill.SkillCatalog;
import ru.infereco.demo.barista.skill.SkillRouter;
import ru.infereco.demo.barista.spec.ProductBrief;
import ru.infereco.demo.barista.spec.SpecLibrary;
import ru.infereco.demo.barista.spec.SpecPipeline;

@Service
public class ChatService {

    private static final String ACTIVE_SPEC = "petclinic-visits.md";
    private static final String SAMPLE_V1 = "v1-petclinic-visits.md";
    private static final String SAMPLE_V2 = "v2-petclinic-visits.md";

    private final InferecoClient infereco;
    private final ConversationStore store;
    private final MeetProperties properties;
    private final MetricsRegistry metrics;
    private final KnowledgeCatalog knowledge;
    private final SelfImproveService selfImprove;
    private final RadioScript radioScript;
    private final SkillCatalog skills;
    private final SkillRouter router;
    private final SpecPipeline pipeline;
    private final SpecLibrary specs;
    private final ProductBrief productBrief;

    public ChatService(
            InferecoClient infereco,
            ConversationStore store,
            MeetProperties properties,
            MetricsRegistry metrics,
            KnowledgeCatalog knowledge,
            SelfImproveService selfImprove,
            RadioScript radioScript,
            SkillCatalog skills,
            SkillRouter router,
            SpecPipeline pipeline,
            SpecLibrary specs,
            ProductBrief productBrief
    ) {
        this.infereco = infereco;
        this.store = store;
        this.properties = properties;
        this.metrics = metrics;
        this.knowledge = knowledge;
        this.selfImprove = selfImprove;
        this.radioScript = radioScript;
        this.skills = skills;
        this.router = router;
        this.pipeline = pipeline;
        this.specs = specs;
        this.productBrief = productBrief;
    }

    public ChatSession start() {
        ChatSession session = store.create();
        session.setSkill("topsp");
        session.add(
                "assistant",
                "TopSP + PetClinic. Загрузите старое и новое СП (два окна справа) или #sp1/#sp2. "
                        + "Затем #sp - что изменилось и что менять на front/back. #task - две задачи. "
                        + "Фон: непрерывное обучение Infereco.",
                "system",
                null
        );
        return session;
    }

    public synchronized ChatSession open(String documentId, String changeId) {
        String doc = documentId == null || documentId.isBlank() ? null : documentId;
        String change = changeId == null || changeId.isBlank() ? null : changeId;
        if ((doc == null) != (change == null)) throw new IllegalArgumentException("Нужны documentId и changeId");
        var existing = store.findContext(doc, change);
        if (existing.isPresent()) return existing.get();
        if (doc == null) return start();
        var selected = pipeline.change(doc, change);
        if (selected == null) throw new IllegalArgumentException("Изменение не найдено");
        ChatSession session = store.create(new ChatContext(doc, change, pipeline.overview(doc).fileName(), selected));
        session.add("assistant", "Обсуждение изменения: " + selected.heading(), "system", null);
        return session;
    }

    public ChatSession enterDoom(UUID sessionId) {
        ChatSession session = store.require(sessionId);
        if (!session.doom()) {
            session.setMode("doom");
            session.add(
                    "assistant",
                    "Рация на связи. Кликни в Doom: стрелки ходить, ctrl огонь, пробел дверь. Спроси куда идти или скажи «бог».",
                    "system",
                    null
            );
        }
        return session;
    }

    public ChatSession get(UUID id) {
        return store.require(id);
    }

    public ChatMessage reply(UUID sessionId, String text, String source) {
        ChatSession session = store.require(sessionId);
        String incoming = text == null ? "" : text.trim();
        session.add("user", incoming, source == null || source.isBlank() ? "text" : source, null);

        String hash = SkillRouter.hashCommand(incoming);
        if (hash != null) {
            ChatMessage commanded = handleHash(session, hash, incoming);
            if (commanded != null) {
                return commanded;
            }
        }

        session.setSkill(router.route(session.doom(), incoming, session.screenBrief(), session.skill()));

        if (!session.doom()
                && "topsp".equals(session.skill())
                && session.context() == null && !hasSpecContext()) {
            return finishLocal(
                    session,
                    "Нет загруженной СП PetClinic. Введите #sp1 (базовая), потом #sp2 (изменение), либо загрузите .md. "
                            + "Для коротких подсказок: #hint.");
        }

        List<KnowledgeDoc> retrieved = knowledge.retrieve(queryFor(session, incoming), session.doom() ? 3 : 6);
        String ragContext = knowledge.formatForPrompt(retrieved);
        if ("topsp".equals(session.skill()) || "hint".equals(session.skill())) {
            String brief = session.context() == null ? productBrief.forTopSp(incoming) : session.context().describe();
            ragContext = brief + (ragContext == null || ragContext.isBlank() ? "" : "\n" + ragContext);
        }

        long started = System.currentTimeMillis();
        String answer;
        try {
            answer = complete(
                    session,
                    properties.models().chat(),
                    null,
                    ragContext,
                    properties.speed().chatMaxTokens());
        } catch (Exception ex) {
            answer = session.doom()
                    ? radioScript.line(0, "нет")
                    : "Infereco сейчас молчит. Повторите реплику через несколько секунд.";
        }
        long latency = System.currentTimeMillis() - started;

        ChatMessage assistant = session.add("assistant", answer, "model", latency, null, null);
        metrics.recordTurn(latency);
        selfImprove.afterTurn(incoming, answer, List.copyOf(retrieved), session.doom());
        return assistant;
    }

    private ChatMessage handleHash(ChatSession session, String hash, String incoming) {
        if (session.context() != null && List.of("sp1", "sp2", "doom").contains(hash)) {
            return finishLocal(session, "Эта команда доступна в общем чате. Контекст изменения сохранён.");
        }
        return switch (hash) {
            case "doom" -> {
                enterDoom(session.id());
                yield session.messages().getLast();
            }
            case "hint" -> {
                session.setSkill("hint");
                String rest = stripHash(incoming);
                if (rest.isBlank()) {
                    yield finishLocal(session, "Режим #hint. Спросите короткий шаг по PetClinic/TopSP. Для СП: #sp1 / #sp2.");
                }
                yield null;
            }
            case "topsp" -> {
                session.setMode("meet");
                session.setSkill("topsp");
                yield finishLocal(session, "Режим TopSP / PetClinic. #sp1 > #sp2 > вопрос про impact > #task.");
            }
            case "sp1" -> {
                session.setSkill("topsp");
                yield finishLocal(session, loadSample(SAMPLE_V1, false));
            }
            case "sp2" -> {
                session.setSkill("topsp");
                yield finishLocal(session, loadSample(SAMPLE_V2, true));
            }
            case "sp" -> {
                session.setSkill("topsp");
                yield finishLocal(session, session.context() == null ? pipeline.describeImpact(null) : session.context().describe());
            }
            case "task" -> {
                session.setSkill("topsp");
                yield finishLocal(session, session.context() == null ? pipeline.createFrontBackTasks(null)
                        : pipeline.createChangeTasks(session.context().fileName(), session.context().change()));
            }
            default -> null;
        };
    }

    private String loadSample(String sampleName, boolean notify) {
        String text = specs.readSample(sampleName);
        specs.ingest(ACTIVE_SPEC, text, notify);
        SpecPipeline.Overview overview = pipeline.overview();
        StringBuilder out = new StringBuilder();
        out.append(notify ? "Загружена изменённая СП PetClinic (" : "Загружена базовая СП PetClinic (")
                .append(sampleName).append(").\n");
        if (overview != null && overview.documentId() != null && !overview.documentId().isBlank()) {
            out.append("Документ: ").append(overview.fileName())
                    .append(" v").append(overview.toVersion())
                    .append(", секций ").append(overview.sections()).append(".\n");
            if (notify) {
                out.append("Изменений: ")
                        .append(overview.summary() == null ? 0 : overview.summary().getOrDefault("total", 0))
                        .append(", потенциально файлов кода: ").append(overview.affectedFiles()).append(".\n");
                if (overview.lastNotice() != null && !overview.lastNotice().isBlank()) {
                    out.append('\n').append(overview.lastNotice());
                }
                out.append("\nСпросите «что изменилось» или введите #task.");
            } else {
                out.append("Это v1 (отмена и правка описания запрещены). Дальше #sp2.");
            }
        }
        return out.toString();
    }

    private boolean hasSpecContext() {
        return pipeline.primaryDocumentId() != null
                && !pipeline.versions(pipeline.primaryDocumentId()).isEmpty();
    }

    private ChatMessage finishLocal(ChatSession session, String answer) {
        ChatMessage message = session.add("assistant", answer, "system", 0L);
        metrics.recordTurn(0);
        return message;
    }

    private static String stripHash(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceFirst("^\\s*#[\\w\\-а-яёА-ЯЁ]+\\s*", "").trim();
    }

    public ChatMessage hint(UUID sessionId, int elapsedSec, String lastCheat, boolean playing) {
        ChatSession session = store.require(sessionId);
        if (!session.doom()) {
            throw new IllegalArgumentException("сначала #doom или секретная кнопка");
        }
        String cheat = lastCheat == null || lastCheat.isBlank() ? "нет" : lastCheat.trim();
        String status = "прошло " + Math.max(elapsedSec, 0) + " с. игра "
                + (playing ? "идёт в окне" : "ещё грузится")
                + ". последний чит: " + cheat
                + ". карта shareware E1M1 Hangar.";
        List<KnowledgeDoc> retrieved = knowledge.retrieve(status + " куда идти hangar чит", 3);
        String ragContext = knowledge.formatForPrompt(retrieved);
        String prompt = """
                Рация морпеху. Одна короткая подсказка: куда идти или какой чит вбить.
                Если даёшь чит, напиши код латиницей слитно, например iddqd.
                Не здоровайся.

                Состояние:
                """ + status;
        long started = System.currentTimeMillis();
        String hint;
        try {
            hint = complete(
                    session,
                    properties.models().fast(),
                    prompt,
                    ragContext,
                    properties.speed().hintMaxTokens());
        } catch (Exception ex) {
            hint = radioScript.line(elapsedSec, cheat);
        }
        long latency = System.currentTimeMillis() - started;
        ChatMessage message = session.add("hint", hint, "hint", latency);
        metrics.recordHint(latency);
        return message;
    }

    public ChatMessage watchScreen(UUID sessionId, String image) {
        ChatSession session = store.require(sessionId);
        if (!session.doom()) {
            throw new IllegalArgumentException("кадр экрана только в Doom");
        }
        String dataUrl = ScreenFrames.dataUrl(image);
        String query = queryFor(session, "hangar кадр куда идти рычаг двор яд дверь exit");
        List<KnowledgeDoc> retrieved = knowledge.retrieve(query, 3);
        String rag = knowledge.formatForPrompt(retrieved);
        String ask = (rag == null || rag.isBlank() ? "" : rag + "\n")
                + "Игрок двигается. Кадр shareware Doom E1M1 Hangar. Что в кадре и куда идти прямо сейчас?";
        long started = System.currentTimeMillis();
        String insight;
        try {
            insight = infereco.completeVision(
                    properties.models().vision(),
                    screenSystem(session),
                    ask,
                    dataUrl,
                    properties.speed().screenMaxTokens(),
                    properties.speed().temperature());
        } catch (Exception ex) {
            throw new IllegalStateException("не разобрал кадр: " + (ex.getMessage() == null ? "модель молчит" : ex.getMessage()), ex);
        }
        long latency = System.currentTimeMillis() - started;
        String draft = insight.replaceAll("(?i)без markdown[^.]*\\.?", "").trim();
        String fallback = "На кадре Doom мало читаемого. Кликни игру и иди дальше, рация повторит.";
        final String cleaned = draft.isBlank() || draft.toLowerCase(Locale.ROOT).contains("не больше 3")
                ? fallback
                : draft;
        session.setSkill("doom");
        if (session.sameScreen(cleaned)) {
            return session.messages().reversed().stream()
                    .filter(message -> "screen".equals(message.source()))
                    .findFirst()
                    .orElseGet(() -> session.add("hint", cleaned, "screen", latency));
        }
        session.rememberScreen(cleaned);
        ChatMessage message = session.add("hint", cleaned, "screen", latency);
        metrics.recordScreen(latency);
        return message;
    }

    private String complete(ChatSession session, String model, String extraUserText, String ragContext, int maxTokens) {
        List<ChatMessage> stored = session.messages();
        List<InferecoClient.Turn> history = new ArrayList<>();
        String userText = extraUserText;
        for (int i = 0; i < stored.size(); i++) {
            ChatMessage message = stored.get(i);
            boolean last = i == stored.size() - 1;
            if ("user".equals(message.role())) {
                if (last && extraUserText == null) {
                    userText = message.text();
                } else {
                    history.add(new InferecoClient.Turn("user", message.text()));
                }
            } else if (("assistant".equals(message.role()) || "hint".equals(message.role()))
                    && !"system".equals(message.source())
                    && !"screen".equals(message.source())) {
                history.add(new InferecoClient.Turn("assistant", message.text()));
            }
        }
        if (userText == null || userText.isBlank()) {
            throw new IllegalStateException("No user text for model call");
        }
        int keep = Math.max(properties.speed().historyMessages(), 2);
        if (history.size() > keep) {
            history = new ArrayList<>(history.subList(history.size() - keep, history.size()));
        }
        String prompt = ragContext == null || ragContext.isBlank()
                ? userText
                : ragContext + "\nРеплика:\n" + userText;
        if (session.hasScreen()) {
            prompt = "Сейчас на экране:\n" + session.screenBrief() + "\n\n" + prompt;
        }
        if (session.context() != null) prompt = "Контекст выбранного изменения (снимок на момент открытия чата):\n" + session.context().describe() + "\n" + prompt;
        String system = session.doom() ? properties.fullDoomSystemPrompt() : chatSystem(session);
        return infereco.complete(
                model,
                system,
                history,
                prompt,
                maxTokens,
                properties.speed().temperature());
    }

    private String queryFor(ChatSession session, String text) {
        String base = text == null ? "" : text;
        if (session.context() != null) base = session.context().change().heading() + " " + base;
        String hint = skills.retrieveHint(session.skill());
        return hint.isBlank() ? base : hint + " " + base;
    }

    private String chatSystem(ChatSession session) {
        String playbook = skills.playbook(session.skill());
        return playbook.isBlank()
                ? properties.fullSystemPrompt()
                : properties.fullSystemPrompt() + "\n\n" + playbook;
    }

    private String screenSystem(ChatSession session) {
        String playbook = skills.playbook("doom");
        return """
                Ты глаза рации в shareware Doom, карта E1M1 Hangar.
                Смотри кадр: комната, двор, яд, рычаг, дверь, враг, броня, дробовик, лестница, EXIT.
                Одна короткая подсказка куда идти прямо сейчас. Не выдумывай то, чего не видно.
                Без markdown, без списков.
                """ + (playbook.isBlank() ? "" : "\n" + playbook);
    }
}
