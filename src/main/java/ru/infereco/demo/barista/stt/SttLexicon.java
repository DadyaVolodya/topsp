package ru.infereco.demo.barista.stt;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog;
import ru.infereco.demo.barista.knowledge.KnowledgeCatalog.KnowledgeDoc;

@Component
public class SttLexicon {

    private static final Pattern TOKEN = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final String[] DOMAIN = {
            "стендап", "ретро", "заказчик", "повестка", "решение", "владелец", "notion",
            "таймбокс", "фасилитация", "совещание", "протокол", "секретарь",
            "экран", "слайд", "zoom", "meet", "teams", "собеседование", "интервью",
            "кандидат", "вопрос", "опыт", "команда",
            "учреждение", "направления", "маршрут", "карта", "живопись", "хореография",
            "вокал", "театр", "яндекс", "логин", "пароль",
            "гнесиных", "гнесин", "гнесенных", "несенных", "колледж", "школа", "добраться",
            "собеседование", "кандидат", "резюме", "слайд",
            "дробовик", "броня", "рычаг", "hangar", "iddqd", "idkfa", "idclip", "рация"
    };

    private final KnowledgeCatalog knowledge;

    public SttLexicon(KnowledgeCatalog knowledge) {
        this.knowledge = knowledge;
    }

    public String correct(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Set<String> vocab = vocab();
        String[] words = text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(best(word, vocab));
        }
        return out.toString();
    }

    public String pick(List<SttJson.Hypothesis> alts) {
        if (alts == null || alts.isEmpty()) {
            return "";
        }
        Set<String> vocab = vocab();
        SttJson.Hypothesis best = alts.getFirst();
        double bestScore = -1;
        for (SttJson.Hypothesis alt : alts) {
            String corrected = correct(alt.text());
            double hits = 0;
            String[] words = corrected.split("\\s+");
            for (String word : words) {
                if (vocab.contains(word)) {
                    hits += 1;
                }
            }
            double score = alt.confidence() + hits * 0.35 + (corrected.equals(alt.text()) ? 0 : 0.05);
            if (score > bestScore) {
                bestScore = score;
                best = new SttJson.Hypothesis(corrected, alt.confidence());
            }
        }
        return best.text();
    }

    Set<String> vocab() {
        Set<String> words = new HashSet<>();
        for (String word : DOMAIN) {
            words.add(word);
        }
        for (KnowledgeDoc doc : knowledge.all()) {
            TOKEN.splitAsStream((doc.title() + " " + doc.body()).toLowerCase(Locale.ROOT))
                    .filter(token -> token.length() > 2)
                    .forEach(words::add);
        }
        return words;
    }

    static String best(String word, Set<String> vocab) {
        if (word.length() < 4 || vocab.contains(word) || word.chars().allMatch(Character::isDigit)) {
            return word;
        }
        int maxDist = word.length() >= 7 ? 2 : 1;
        String found = null;
        int bestDist = maxDist + 1;
        for (String candidate : vocab) {
            if (Math.abs(candidate.length() - word.length()) > maxDist) {
                continue;
            }
            int dist = distance(word, candidate);
            if (dist < bestDist) {
                bestDist = dist;
                found = candidate;
            }
        }
        return found != null && bestDist <= maxDist ? found : word;
    }

    static int distance(String left, String right) {
        int n = left.length();
        int m = right.length();
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char a = left.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = a == right.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[m];
    }
}
