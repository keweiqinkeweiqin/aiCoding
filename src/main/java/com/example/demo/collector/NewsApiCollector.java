package com.example.demo.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * NewsAPI.org collector - global English tech/business news.
 * API: https://newsapi.org/v2/top-headlines?category=technology&language=en&pageSize=50&apiKey=xxx
 */
@Component
public class NewsApiCollector {

    private static final Logger log = LoggerFactory.getLogger(NewsApiCollector.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${collector.newsapi.key:}")
    private String apiKey;

    public List<RssCollector.RssItem> collect() {
        List<RssCollector.RssItem> items = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("NewsAPI key not configured, skip");
            return items;
        }
        try {
            String url = "https://newsapi.org/v2/top-headlines?category=technology&language=en&pageSize=50&apiKey=" + apiKey;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());

            if (!"ok".equals(root.path("status").asText())) {
                log.warn("NewsAPI error: {}", root.path("message").asText());
                return items;
            }
            for (JsonNode a : root.path("articles")) {
                String title = a.path("title").asText("");
                String desc = a.path("description").asText("");
                String link = a.path("url").asText("");
                String source = a.path("source").path("name").asText("NewsAPI");
                if (title.isBlank() || "[Removed]".equals(title)) continue;
                items.add(new RssCollector.RssItem(title.trim(), desc.trim(), link, "NewsAPI-" + source));
            }
            log.info("NewsAPI collected {} items", items.size());
        } catch (Exception e) {
            log.error("NewsAPI collect failed: {}", e.getMessage());
        }
        return items;
    }
}
