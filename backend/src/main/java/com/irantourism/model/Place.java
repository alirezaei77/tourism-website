package com.irantourism.model;

import java.util.List;

public record Place(
    String id, String name, String province, String locality,
    String category, String type, double lat, double lng,
    String shortDescription, String description, String image,
    String difficulty, String season, String duration,
    String status, List<String> tags, String visit, String alert,
    boolean verified, String color
) {
    public Place withId(String newId) {
        return new Place(newId, name, province, locality, category, type, lat, lng,
            shortDescription, description, image, difficulty, season, duration,
            status, tags, visit, alert, verified, color);
    }
}