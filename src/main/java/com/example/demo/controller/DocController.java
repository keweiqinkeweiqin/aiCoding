package com.example.demo.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/docs")
public class DocController {

    @GetMapping(value = "/api-frontend", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> apiFrontend() throws IOException {
        return readDoc("docs/API文档_华尔街之眼_前端对接.md");
    }

    @GetMapping(value = "/api-spec", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> apiSpec() throws IOException {
        return readDoc(".kiro/specs/wall-street-eye/api-doc.md");
    }

    private ResponseEntity<String> readDoc(String relativePath) throws IOException {
        Path file = Path.of(relativePath);
        if (Files.exists(file)) {
            return ResponseEntity.ok(Files.readString(file, StandardCharsets.UTF_8));
        }
        return ResponseEntity.notFound().build();
    }
}
