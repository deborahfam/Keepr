package com.keepr.app.domain;

/** Immutable offline snapshot. Monetary values are decimal text, never floating point. */
public record SavedItem(String id, String url, String title, String description,
        String siteName, String imageUrl, String price, String currency, String contact,
        String notes, String categoryId, Status status, PreviewState previewState,
        String previewError, long createdAt, long updatedAt, String sharedText,
        java.util.List<String> tags, java.util.List<CachedMedia> media, int expectedPhotos, int pendingPhotos, int failedPhotos) {
    public SavedItem(String id,String url,String title,String description,String siteName,String imageUrl,String price,String currency,String contact,String notes,String categoryId,Status status,PreviewState previewState,String previewError,long createdAt,long updatedAt,String sharedText,java.util.List<String> tags,java.util.List<CachedMedia> media) {
        this(id,url,title,description,siteName,imageUrl,price,currency,contact,notes,categoryId,status,previewState,previewError,createdAt,updatedAt,sharedText,tags,media,0,0,0);
    }
    public String captureSummary() {
        int saved=media.size(),total=Math.max(expectedPhotos,saved+pendingPhotos+failedPhotos);
        String counts=total>0?saved+" of "+total+" photos saved":"No photos saved";
        if(previewState==PreviewState.PENDING || pendingPhotos>0) return "Downloading · "+(total==0?"discovering available photos":counts);
        if(failedPhotos>0 || total>saved || previewState==PreviewState.FAILED) return "Needs attention · "+counts;
        return total>0?counts:"Saved locally · no photos available";
    }
    public enum Status { ACTIVE, REACHED_OUT, PURCHASED, ARCHIVED }
    public enum PreviewState { PENDING, READY, FAILED }
}
