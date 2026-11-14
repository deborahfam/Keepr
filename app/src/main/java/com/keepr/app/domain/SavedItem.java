package com.keepr.app.domain;

/** Immutable offline snapshot. Monetary values are decimal text, never floating point. */
public record SavedItem(String id, String url, String title, String description,
        String siteName, String imageUrl, String price, String currency, String contact,
        String notes, String categoryId, Status status, PreviewState previewState,
        String previewError, long createdAt, long updatedAt, String sharedText,
        java.util.List<String> tags, java.util.List<CachedMedia> media) {
    public enum Status { ACTIVE, REACHED_OUT, PURCHASED, ARCHIVED }
    public enum PreviewState { PENDING, READY, FAILED }
}
