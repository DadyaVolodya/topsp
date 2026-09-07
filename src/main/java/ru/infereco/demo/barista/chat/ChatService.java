package ru.infereco.demo.barista.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.infereco.demo.barista.config.MeetProperties;
import ru.infereco.demo.barista.improve.SelfImproveService;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog.KnowledgeDoc;
import ru.infereco.demo.barista.metrics.MetricsRegistry;
import ru.infereco.demo.barista.pto.PtoClient;
import ru.infereco.demo.barista.pto.PtoPages;
import ru.infereco.demo.barista.pto.PtoPlace;
import ru.infereco.demo.barista.skill.SkillCatalog;
import ru.infereco.demo.barista.skill.SkillRouter;

@Service
public class ChatService {

    private final InferecoClient infereco;
    private final ConversationStore store;
    private final MeetProperties properties;
    private final MetricsRegistry metrics;
    private final KnowledgeCatalog knowledge;
    private final SelfImproveService selfImprove;
    private final RadioScript radioScript;
    private final SkillCatalog skills;
    private final SkillRouter router;
    private final PtoClient pto;

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
            PtoClient pto
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
        this.pto = pto;
    }

    public ChatSession start() {
        ChatSession session = store.create();
        session.add(
                "assistant",
                "Я напарник. Созвон: только голос. ПТО: системные постановки и dev.aisto.local. Doom: рация смотрит кадр игры.",
                "system",
                null
        );
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
        session.add("user", text, source == null || source.isBlank() ? "text" : source, null);
        session.setSkill(router.route(session.doom(), text, session.screenBrief(), session.skill()));
        PtoPlace place = session.doom() ? null : pto.find(text, session.screenBrief()).orElse(null);
        PtoPages.PtoPage page = place != null || session.doom()
                ? null
                : PtoPages.resolve(text, session.screenBrief(), ptoSite()).orElse(null);
        if (place != null || page != null) {
            session.setSkill("pto-site");
        }

        List<KnowledgeDoc> retrieved = knowledge.retrieve(queryFor(session, text), 3);
        String ragContext = knowledge.formatForPrompt(retrieved);
        if (place != null) {
            ragContext = place.promptBlock() + (ragContext == null || ragContext.isBlank() ? "" : "\n" + ragContext);
        } else if (page != null) {
            ragContext = page.promptBlock() + (ragContext == null || ragContext.isBlank() ? "" : "\n" + ragContext);
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
        if (place != null && answer != null && answer.toLowerCase(java.util.Locale.ROOT).contains("молчит")) {
            answer = "Это «" + place.name() + "», " + place.address()
                    + ". Открою карточку на dev.aisto.local. На карточке жми «Постройте маршрут».";
        } else if (page != null && answer != null && answer.toLowerCase(java.util.Locale.ROOT).contains("молчит")) {
            answer = "Открываю «" + page.title() + "» на dev.aisto.local" + page.path() + ".";
        }
        long latency = System.currentTimeMillis() - started;

        ChatMessage assistant = session.add(
                "assistant",
                answer,
                "model",
                latency,
                place != null ? place.openUrl() : (page == null ? null : page.openUrl()),
                place == null ? null : place.cardUrl());
        metrics.recordTurn(latency);
        selfImprove.afterTurn(text, answer, List.copyOf(retrieved), session.doom());
        return assistant;
    }

    public ChatMessage hint(UUID sessionId, int elapsedSec, String lastCheat, boolean playing) {
        ChatSession session = store.require(sessionId);
        if (!session.doom()) {
            throw new IllegalArgumentException("сначала секретная кнопка");
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
        String query = session.doom()
                ? queryFor(session, "hangar кадр куда идти рычаг двор яд дверь exit")
                : queryFor(session, (session.hasScreen() ? session.screenBrief() + " " : "") + "экран слайд карта маршрут");
        List<KnowledgeDoc> retrieved = knowledge.retrieve(query, 3);
        String rag = knowledge.formatForPrompt(retrieved);
        String ask = (rag == null || rag.isBlank() ? "" : rag + "\n")
                + (session.doom()
                        ? "Игрок двигается. Кадр shareware Doom E1M1 Hangar. Что в кадре и куда идти прямо сейчас?"
                        : "Кадр с экрана человека. Что видишь и чем помочь прямо сейчас?");
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
        String fallback = session.doom()
                ? "На кадре Doom мало читаемого. Кликни игру и иди дальше, рация повторит."
                : "На кадре мало понятного текста. Шарьте окно встречи, слайд или сайт ПТО.";
        final String cleaned = draft.isBlank() || draft.toLowerCase(java.util.Locale.ROOT).contains("не больше 3")
                ? fallback
                : draft;
        session.setSkill(router.route(session.doom(), cleaned, cleaned, session.skill()));
        PtoPlace place = session.doom() ? null : pto.find(cleaned, session.screenBrief()).orElse(null);
        PtoPages.PtoPage page = place != null || session.doom()
                ? null
                : PtoPages.resolve(cleaned, session.screenBrief(), ptoSite()).orElse(null);
        if (place != null || page != null) {
            session.setSkill("pto-site");
        }
        if (session.sameScreen(cleaned)) {
            return session.messages().reversed().stream()
                    .filter(message -> "screen".equals(message.source()))
                    .findFirst()
                    .orElseGet(() -> session.add(
                            "hint",
                            cleaned,
                            "screen",
                            latency,
                            place != null ? place.openUrl() : (page == null ? null : page.openUrl()),
                            place == null ? null : place.cardUrl()));
        }
        session.rememberScreen(cleaned);
        ChatMessage message = session.add(
                "hint",
                cleaned,
                "screen",
                latency,
                place != null ? place.openUrl() : (page == null ? null : page.openUrl()),
                place == null ? null : place.cardUrl());
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
        if (session.doom()) {
            String playbook = skills.playbook("doom");
            return """
                    Ты глаза рации в shareware Doom, карта E1M1 Hangar.
                    Смотри кадр: комната, двор, яд, рычаг, дверь, враг, броня, дробовик, лестница, EXIT.
                    Одна короткая подсказка куда идти прямо сейчас. Не выдумывай то, чего не видно.
                    Без markdown, без списков.
                    """ + (playbook.isBlank() ? "" : "\n" + playbook);
        }
        String primary = skills.playbook(session.skill());
        StringBuilder prompt = new StringBuilder(properties.fullScreenPrompt());
        if (!primary.isBlank()) {
            prompt.append("\n\n").append(primary);
        }
        prompt.append("""
                
                Если кадр явно про другую сцену, назови её первым предложением и работай по ней.
                Zoom/Meet/Teams/слайд = собеседование. dev.aisto.local, карта учебных заведений, логин pto-pp = обучалка ПТО.
                Не смешивай клики сайта ПТО с советами на собеседовании.
                """);
        return prompt.toString();
    }

    private String ptoSite() {
        MeetProperties.Pto demo = properties.demo() == null ? null : properties.demo().pto();
        return PtoPages.siteUrl(demo == null ? null : demo.url());
    }
}
