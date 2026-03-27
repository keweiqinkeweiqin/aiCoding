package com.example.demo.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.xml.sax.InputSource;

@Component
public class RssCollector {

    private static final Logger log = LoggerFactory.getLogger(RssCollector.class);
    private final RestTemplate restTemplate;

    public RssCollector(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 从RSS源采集新闻，返回简单的标题+内容+链接
     */
    public List<RssItem> collect(String rssUrl, String sourceName) {
        List<RssItem> items = new ArrayList<>();
        try {
            String xml = restTemplate.getForObject(rssUrl, String.class);
            if (xml == null || xml.isBlank()) {
                log.warn("RSS源返回空内容: {}", rssUrl);
                return items;
            }

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            // 兼容 RSS 2.0 (item) 和 Atom (entry)
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
            log.info("从 {} 采集到 {} 条新闻", sourceName, items.size());
        } catch (Exception e) {
            log.error("采集RSS失败 [{}]: {}", sourceName, e.getMessage());
        }
        return items;
    }

    private String getText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
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
        // RSS 2.0: <link>url</link>
        NodeList links = parent.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Node node = links.item(i);
            // Atom: <link href="url"/>
            if (node instanceof Element el) {
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
