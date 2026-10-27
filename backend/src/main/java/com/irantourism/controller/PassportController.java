package com.irantourism.controller;

import com.irantourism.model.Place;
import com.irantourism.store.InMemoryStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passport")
public class PassportController {
    private final InMemoryStore store;

    public PassportController(InMemoryStore store) { this.store = store; }

    private String clientId(String h) {
        return (h == null || h.isBlank()) ? "anon" : h;
    }

    @GetMapping("/saved")
    public List<Place> listSaved(@RequestHeader(value="X-Client-Id", required=false) String cid) {
        return store.getSavedPlaces(clientId(cid));
    }

    @GetMapping("/visited")
    public List<Place> listVisited(@RequestHeader(value="X-Client-Id", required=false) String cid) {
        return store.getVisitedPlaces(clientId(cid));
    }

    @PostMapping("/saved/{id}")
    public ResponseEntity<Void> addSaved(@PathVariable String id,
                                          @RequestHeader(value="X-Client-Id", required=false) String cid) {
        if (store.getPlace(id).isEmpty()) return ResponseEntity.notFound().build();
        store.addSaved(clientId(cid), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/saved/{id}")
    public ResponseEntity<Void> removeSaved(@PathVariable String id,
                                             @RequestHeader(value="X-Client-Id", required=false) String cid) {
        store.removeSaved(clientId(cid), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/visited/{id}")
    public ResponseEntity<Void> addVisited(@PathVariable String id,
                                            @RequestHeader(value="X-Client-Id", required=false) String cid) {
        if (store.getPlace(id).isEmpty()) return ResponseEntity.notFound().build();
        store.addVisited(clientId(cid), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/visited/{id}")
    public ResponseEntity<Void> removeVisited(@PathVariable String id,
                                               @RequestHeader(value="X-Client-Id", required=false) String cid) {
        store.removeVisited(clientId(cid), id);
        return ResponseEntity.noContent().build();
    }
}