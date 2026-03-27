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
 * TianAPI finance news collector.
 * API: https://apis.tianapi.com/caijing/index?key=xxx&num=50
 */
@Component
public class TianApiCollector {

    private static final Logger log = LoggerFactory.getLogger(TianApiCollector.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${collector.tianapi.key:}")
    private String apiKey;

    public List<RssCollector.RssItem> collect() {
        List<RssCollector.RssItem> items = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("TianAPI key not configured, skip");
            return items;
        }
        try {
            String url = "https://apis.tianapi.com/caijing/index?key=" + apiKey + "&num=50";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            int code = root.has("code") ? root.get("code").asInt() : -1;
            if (code != 200) {
                log.warn("TianAPI returned code {}: {}", code, root.has("msg") ? root.get("msg").asText() : "");
                return items;
            }

            JsonNode result = root.get("result");
            if (result == null || !result.has("newslist")) return items;

            JsonNode newsList = result.get("newslist");
            for (JsonNode n : newsList) {
                String title = n.has("title") ? n.get("title").asText() : "";
                String content = n.has("description") ? n.get("description").asText() : "";
                String link = n.has("url") ? n.get("url").asText() : "";
                if (title.isBlank()) continue;
                items.add(new RssCollector.RssItem(title.trim(), content.trim(), link, "TianAPI"));
            }
            log.info("TianAPI collected {} items", items.size());
        } catch (Exception e) {
            log.error("TianAPI collect failed: {}", e.getMessage());
        }
        return items;
    }
}
