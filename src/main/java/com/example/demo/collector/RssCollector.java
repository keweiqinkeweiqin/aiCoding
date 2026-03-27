package com.example.demo.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.xml.sax.InputSource;

@Component
public class RssCollector {

    private static final Logger log = LoggerFactory.getLogger(RssCollector.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<RssItem> collect(String rssUrl, String sourceName) {
        List<RssItem> items = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rssUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String xml = response.body();
            if (xml == null || xml.isBlank()) {
                log.warn("RSS source returned empty: {}", rssUrl);
                return items;
            }

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            NodeList nodes = doc.getElementsByTagName("item");
            if (nodes.getLength() == 0) {
                nodes = doc.getElementsByTagName("entry");
            }

            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String title = getText(el, "title");
                String content = getFirstNonEmpty(el, "description", "content", "summary");
                String link = getLink(el);
                if (title != null && !title.isBlank()) {
                    items.add(new RssItem(title.trim(), content != null ? content.trim() : "", link, sourceName));
                }
            }
            log.info("Collected {} items from {}", items.size(), sourceName);
        } catch (Exception e) {
            log.error("RSS collect failed [{}]: {}", sourceName, e.getMessage());
        }
        return items;
    }

    private String getText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) return list.item(0).getTextContent();
        return null;
    }

    private String getFirstNonEmpty(Element parent, String... tagNames) {
        for (String tag : tagNames) {
            String text = getText(parent, tag);
            if (text != null && !text.isBlank()) return text;
        }
        return null;
    }

    private String getLink(Element parent) {
        NodeList links = parent.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                String href = el.getAttribute("href");
                if (href != null && !href.isBlank()) return href;
                String text = el.getTextContent();
                if (text != null && !text.isBlank()) return text.trim();
            }
        }
        return "";
    }

    public record RssItem(String title, String content, String link, String sourceName) {}
}
