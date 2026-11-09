package com.keepr.app.domain;

/** relativePath points into app-private files, not the evictable Android cache directory. */
public record CachedMedia(String id, String savedItemId, String sourceUrl, String relativePath,
        String mimeType, long byteCount, String sha256, long cachedAt) {}
