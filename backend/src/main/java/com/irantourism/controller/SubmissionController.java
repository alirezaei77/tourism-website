package com.irantourism.controller;

import com.irantourism.model.Place;
import com.irantourism.model.Submission;
import com.irantourism.store.InMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private final InMemoryStore store;

    public SubmissionController(InMemoryStore store) { this.store = store; }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String name = str(body.get("name"));
        String type = str(body.get("type"));
        String reason = str(body.get("reason"));
        if (name.isBlank() || type.isBlank() || reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "فیلدهای ضروری ناقص هستند."));
        }
        Submission in = new Submission(null, name, type,
            str(body.get("province")), str(body.get("locality")),
            asDouble(body.get("lat")), asDouble(body.get("lng")),
            reason, str(body.get("source")), str(body.get("access")),
            null, 0);
        Submission saved = store.createSubmission(in);
        Place place = store.getPlace(saved.id()).orElseThrow();
        return ResponseEntity.ok(place);
    }

    @PostMapping("/report")
    public ResponseEntity<?> report(@RequestBody Map<String, Object> body) {
        String reason = str(body.get("reason"));
        if (reason.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "توضیح مشکل الزامی است."));
        }
        Submission in = new Submission(null, str(body.get("name")), "گزارش",
            str(body.get("province")), str(body.get("locality")),
            asDouble(body.get("lat")), asDouble(body.get("lng")),
            reason, str(body.get("source")), str(body.get("access")),
            "گزارش کاربر", 0);
        Submission saved = store.createSubmission(in);
        return ResponseEntity.ok(Map.of("id", saved.id(), "status", saved.status()));
    }

    @GetMapping
    public List<Submission> list() { return store.listSubmissions(); }

    private String str(Object o) { return o == null ? "" : o.toString().trim(); }

    private Double asDouble(Object o) {
        if (o == null || o.toString().isBlank()) return null;
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return null; }
    }
}