package com.irantourism.controller;

import com.irantourism.model.Place;
import com.irantourism.store.InMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/places")
public class PlaceController {
    private final InMemoryStore store;

    public PlaceController(InMemoryStore store) { this.store = store; }

    @GetMapping
    public Map<String, Object> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10000") int size
    ) {
        List<Place> all = new ArrayList<>(store.listPlaces());
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return Map.of(
            "items", all.subList(from, to),
            "total", all.size(),
            "page", page,
            "size", size
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Place> get(@PathVariable String id) {
        return store.getPlace(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/bulk")
    public Map<String, Object> bulk(@RequestBody List<Place> items) {
        List<Place> toSave = new ArrayList<>();
        for (Place p : items) {
            String id = (p.id() == null || p.id().isBlank())
                ? "place-" + UUID.randomUUID().toString().substring(0, 8) : p.id();
            toSave.add(p.withId(id));
        }
        store.saveAll(toSave);
        return Map.of("inserted", toSave.size(), "total", store.listPlaces().size());
    }
}