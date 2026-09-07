package ru.infereco.demo.barista.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class SkillCatalog {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillCatalog(MeetProperties properties) {
        for (Skill skill : load()) {
            skills.put(skill.id(), fill(skill, properties));
        }
    }

    public List<Skill> all() {
        return List.copyOf(skills.values());
    }

    public Skill get(String id) {
        if (id == null || id.isBlank()) {
            return skills.get("interview");
        }
        Skill found = skills.get(id);
        return found != null ? found : skills.get("interview");
    }

    public String playbook(String id) {
        Skill skill = get(id);
        if (skill == null || skill.body().isBlank()) {
            return "";
        }
        return "Активный скил «" + skill.title() + "»:\n" + skill.body();
    }

    public String retrieveHint(String id) {
        Skill skill = get(id);
        if (skill == null) {
            return "";
        }
        return skill.id() + " " + String.join(" ", skill.triggers());
    }

    static Skill fill(Skill skill, MeetProperties properties) {
        MeetProperties.Demo demo = properties == null ? null : properties.demo();
        MeetProperties.Pto pto = demo == null ? null : demo.pto();
        String url = pto == null || pto.url() == null ? "" : pto.url().trim();
        String api = pto == null ? "" : ru.infereco.demo.barista.pto.PtoPages.apiUrl(url, pto.api());
        String login = pto == null || pto.login() == null ? "" : pto.login().trim();
        String password = pto == null || pto.password() == null ? "" : pto.password().trim();
        if (password.isBlank()) {
            password = "пароль в config/application-local.yml";
        }
        String body = skill.body()
                .replace("{{pto.url}}", url)
                .replace("{{pto.api}}", api)
                .replace("{{pto.login}}", login)
                .replace("{{pto.password}}", password);
        return new Skill(skill.id(), skill.title(), skill.triggers(), body);
    }

    static Skill parse(String filename, String raw) {
        String id = filename.replace(".md", "");
        String title = id;
        List<String> triggers = new ArrayList<>();
        String body = raw == null ? "" : raw.trim();
        if (body.startsWith("---")) {
            int end = body.indexOf("\n---", 3);
            if (end > 0) {
                String front = body.substring(3, end).trim();
                body = body.substring(end + 4).trim();
                for (String line : front.split("\\R")) {
                    int colon = line.indexOf(':');
                    if (colon < 1) {
                        continue;
                    }
                    String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(colon + 1).trim();
                    if ("id".equals(key) && !value.isBlank()) {
                        id = value;
                    } else if ("title".equals(key) && !value.isBlank()) {
                        title = value;
                    } else if ("triggers".equals(key) && !value.isBlank()) {
                        for (String token : value.split(",")) {
                            String trigger = token.trim().toLowerCase(Locale.ROOT);
                            if (!trigger.isBlank()) {
                                triggers.add(trigger);
                            }
                        }
                    }
                }
            }
        }
        return new Skill(id, title, triggers, body);
    }

    private List<Skill> load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<Skill> loaded = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources("classpath:skills/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename() == null ? "unknown.md" : resource.getFilename();
                String raw = resource.getContentAsString(StandardCharsets.UTF_8);
                loaded.add(parse(filename, raw));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load skills", ex);
        }
        loaded.sort((a, b) -> a.id().compareTo(b.id()));
        return loaded;
    }
}
