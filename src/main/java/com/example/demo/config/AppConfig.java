package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);

        RestTemplate rt = new RestTemplate(factory);
        // Support UTF-8 for XML responses
        rt.getMessageConverters().stream()
                .filter(c -> c instanceof StringHttpMessageConverter)
                .map(c -> (StringHttpMessageConverter) c)
                .forEach(c -> {
                    c.setDefaultCharset(StandardCharsets.UTF_8);
                    // Accept all media types including application/xml
                    c.setSupportedMediaTypes(java.util.List.of(
                            org.springframework.http.MediaType.TEXT_PLAIN,
                            org.springframework.http.MediaType.TEXT_XML,
                            org.springframework.http.MediaType.APPLICATION_XML,
                            org.springframework.http.MediaType.APPLICATION_JSON,
                            new org.springframework.http.MediaType("application", "*+xml")
                    ));
                });
        return rt;
    }
}
