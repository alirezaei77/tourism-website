package com.irantourism.controller;

import com.irantourism.store.InMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/newsletter")
public class NewsletterController {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final InMemoryStore store;

    public NewsletterController(InMemoryStore store) { this.store = store; }

    @PostMapping
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> body) {
        String email = body.get("email") == null ? "" : body.get("email").toString().trim();
        if (!EMAIL.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "ایمیل نامعتبر است."));
        }
        boolean added = store.subscribe(email);
        return ResponseEntity.ok(Map.of("subscribed", added, "email", email));
    }
}
