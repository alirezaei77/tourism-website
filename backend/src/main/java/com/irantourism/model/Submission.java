package com.irantourism.model;

public record Submission(
    String id, String name, String type, String province, String locality,
    Double lat, Double lng, String reason, String source, String access,
    String status, long createdAt
) {}