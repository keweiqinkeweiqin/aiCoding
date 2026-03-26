package com.example.demo.service;

import com.example.demo.model.Word;
import com.example.demo.repository.WordRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WordService {

    private final WordRepository wordRepository;

    public WordService(WordRepository wordRepository) {
        this.wordRepository = wordRepository;
    }

    public List<Word> findAll() {
        return wordRepository.findAll();
    }

    public List<Word> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return wordRepository.findAll();
        }
        return wordRepository.findByContentContaining(keyword.trim());
    }

    public Word save(String content) {
        Word word = new Word(content.trim());
        return wordRepository.save(word);
    }

    public void deleteById(Long id) {
        wordRepository.deleteById(id);
    }
}
