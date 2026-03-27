package com.example.demo.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 自定义 Embedding 客户端（因为 Embedding 和 Chat 用不同的 Secret Key）
 */
@Component
public class EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${embedding.api-url}")
    private String apiUrl;

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.model}")
    private String model;

    public EmbeddingClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 对单条文本生成 Embedding 向量
     */
    public float[] embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", text
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            JsonNode data = objectMapper.readTree(response.getBody())
                    .get("data").get(0).get("embedding");

            float[] vector = new float[data.size()];
            for (int i = 0; i < data.size(); i++) {
                vector[i] = (float) data.get(i).asDouble();
            }
            return vector;
        } catch (Exception e) {
            log.error("Embedding调用失败: {}", e.getMessage());
            return new float[0];
        }
    }

    /**
     * 批量 Embedding（建议每批不超过4条）
     */
    public List<float[]> embedBatch(List<String> texts) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "input", texts
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, request, String.class);
            JsonNode dataArray = objectMapper.readTree(response.getBody()).get("data");

            return java.util.stream.IntStream.range(0, dataArray.size())
                    .mapToObj(i -> {
                        JsonNode embedding = dataArray.get(i).get("embedding");
                        float[] vector = new float[embedding.size()];
                        for (int j = 0; j < embedding.size(); j++) {
                            vector[j] = (float) embedding.get(j).asDouble();
                        }
                        return vector;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("批量Embedding调用失败: {}", e.getMessage());
            return List.of();
        }
    }
}
