package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A simple REST controller exposing two endpoints:
 *   GET /            -> a greeting message
 *   GET /health      -> a health-check used by Docker / the pipeline
 */
@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, String> hello(@RequestParam(defaultValue = "World") String name) {
        return Map.of(
                "message", "Hello, " + name + "!",
                "app", "java-cicd-demo"
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
