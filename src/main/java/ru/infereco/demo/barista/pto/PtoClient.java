package ru.infereco.demo.barista.pto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.infereco.demo.barista.config.MeetProperties;

@Component
public class PtoClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
    private static final Logger LOG = LoggerFactory.getLogger(PtoClient.class);

    private final HttpClient http = PtoHttp.client();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MeetProperties properties;

    public PtoClient(MeetProperties properties) {
        this.properties = properties;
    }

    public Optional<PtoPlace> find(String userText) {
        return find(userText, "");
    }

    public Optional<PtoPlace> find(String userText, String screenBrief) {
        PtoQuery query = PtoQuery.parse(userText, screenBrief);
        if (!query.searchable()) {
            return Optional.empty();
        }
        MeetProperties.Pto pto = properties.demo() == null ? null : properties.demo().pto();
        if (pto == null || pto.url() == null || pto.url().isBlank()) {
            return Optional.empty();
        }
        String stand = PtoPages.siteUrl(pto.url());
        String api = PtoPages.apiUrl(pto.url(), pto.api());
        String auth = basic(pto.login(), pto.password());
        try {
            Optional<PtoPlace> fromSearch = search(api, auth, query, stand);
            if (fromSearch.isPresent()) {
                return fromSearch;
            }
            return suggestThenGet(api, auth, query, stand);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("поиск ПТО прерван");
            return Optional.empty();
        } catch (Exception ex) {
            LOG.warn("поиск ПТО не вышел: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<PtoPlace> search(String api, String auth, PtoQuery query, String stand) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("query", query.term());
        body.put("page", 0);
        body.put("size", 20);
        HttpRequest request = HttpRequest.newBuilder(URI.create(api + "/pto/institutions/search"))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Authorization", auth)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.warn("search ПТО HTTP {}", response.statusCode());
            return Optional.empty();
        }
        return PtoPlaces.fromSearch(mapper.readTree(response.body()), stand, query);
    }

    private Optional<PtoPlace> suggestThenGet(String api, String auth, PtoQuery query, String stand) throws Exception {
        String encoded = URLEncoder.encode(query.term(), StandardCharsets.UTF_8);
        HttpRequest suggest = HttpRequest.newBuilder(URI.create(api + "/pto/institutions/suggest?query=" + encoded))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Authorization", auth)
                .GET()
                .build();
        HttpResponse<String> suggested = http.send(suggest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (suggested.statusCode() < 200 || suggested.statusCode() >= 300) {
            return Optional.empty();
        }
        Optional<java.util.UUID> id = PtoPlaces.firstSuggestId(mapper.readTree(suggested.body()));
        if (id.isEmpty()) {
            return Optional.empty();
        }
        HttpRequest get = HttpRequest.newBuilder(URI.create(api + "/pto/institutions/" + id.get()))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Authorization", auth)
                .GET()
                .build();
        HttpResponse<String> card = http.send(get, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (card.statusCode() < 200 || card.statusCode() >= 300) {
            return Optional.empty();
        }
        JsonNode node = mapper.readTree(card.body());
        ObjectNode wrap = mapper.createObjectNode();
        wrap.set("content", mapper.createArrayNode().add(node));
        return PtoPlaces.fromSearch(wrap, stand, query);
    }

    private static String basic(String login, String password) {
        String user = login == null ? "" : login;
        String pass = password == null ? "" : password;
        String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
