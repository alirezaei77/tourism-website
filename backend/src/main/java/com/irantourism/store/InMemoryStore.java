package com.irantourism.store;

import com.irantourism.model.Place;
import com.irantourism.model.Submission;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class InMemoryStore {
    private final Map<String, Place> places = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> savedByClient = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> visitedByClient = new ConcurrentHashMap<>();
    private final List<Submission> submissions = new CopyOnWriteArrayList<>();
    private final Set<String> newsletterEmails = ConcurrentHashMap.newKeySet();
    private final AtomicLong submissionSeq = new AtomicLong(1);

    public Collection<Place> listPlaces() { return places.values(); }

    public Optional<Place> getPlace(String id) { return Optional.ofNullable(places.get(id)); }

    public void savePlace(Place p) { places.put(p.id(), p); }

    public void saveAll(List<Place> list) { list.forEach(this::savePlace); }

    public Set<String> getSaved(String clientId) {
        return savedByClient.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());
    }

    public Set<String> getVisited(String clientId) {
        return visitedByClient.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet());
    }

    public void addSaved(String clientId, String placeId) { getSaved(clientId).add(placeId); }
    public void removeSaved(String clientId, String placeId) { getSaved(clientId).remove(placeId); }
    public void addVisited(String clientId, String placeId) { getVisited(clientId).add(placeId); }
    public void removeVisited(String clientId, String placeId) { getVisited(clientId).remove(placeId); }

    public List<Place> getSavedPlaces(String clientId) {
        Set<String> ids = getSaved(clientId);
        return ids.stream().map(places::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<Place> getVisitedPlaces(String clientId) {
        Set<String> ids = getVisited(clientId);
        return ids.stream().map(places::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public Submission createSubmission(Submission input) {
        String id = "sub-" + submissionSeq.getAndIncrement();
        Submission s = new Submission(id, input.name(), input.type(), input.province(),
            input.locality(), input.lat(), input.lng(), input.reason(), input.source(),
            input.access(), "نیازمند بررسی", System.currentTimeMillis());
        submissions.add(s);
        Place newPlace = new Place(
            id, input.name(), input.province()==null?"":input.province(),
            input.locality()==null?"":input.locality(),
            mapTypeToCategory(input.type()), input.type(),
            input.lat()==null?0:input.lat(), input.lng()==null?0:input.lng(),
            input.reason(), input.reason(), null, "نامشخص", "چهارفصل", "نامشخص",
            "نیازمند بررسی", List.of("پیشنهاد کاربر"), "شرایط بازدید نامشخص است.",
            input.access(), false, "#43865f"
        );
        places.put(id, newPlace);
        return s;
    }

    public List<Submission> listSubmissions() { return submissions; }

    public boolean subscribe(String email) { return newsletterEmails.add(email.toLowerCase()); }

    private String mapTypeToCategory(String type) {
        if (type == null) return "فرهنگی";
        if (type.contains("طبیعی")) return "طبیعی";
        if (type.contains("تاریخی")) return "تاریخی";
        if (type.contains("زمین")) return "زمین‌شناسی";
        if (type.contains("خوراک")) return "خوراک";
        if (type.contains("مسیر")) return "مسیر";
        return "فرهنگی";
    }
}
