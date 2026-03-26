package com.example.demo.controller;

import com.example.demo.model.Word;
import com.example.demo.service.WordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/words")
public class WordController {

    private final WordService wordService;

    public WordController(WordService wordService) {
        this.wordService = wordService;
    }

    @GetMapping
    public List<Word> list(@RequestParam(required = false) String keyword) {
        return wordService.search(keyword);
    }

    @PostMapping
    public ResponseEntity<Word> create(@RequestBody Word word) {
        Word saved = wordService.save(word.getContent());
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        wordService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
