package com.keepr.app.data;

public record PreviewMetadata(String resolvedUrl, String title, String description, String siteName,
        String imageUrl, String price, String currency, String contact, java.util.List<String> imageUrls, int expectedPhotoCount, String location, String productType, String pricePeriod, String metadataSource) {
    public PreviewMetadata(String resolvedUrl,String title,String description,String siteName,String imageUrl,String price,String currency,String contact,java.util.List<String> imageUrls,int expectedPhotoCount) {
        this(resolvedUrl,title,description,siteName,imageUrl,price,currency,contact,imageUrls,expectedPhotoCount,"","","","");
    }
    public PreviewMetadata(String resolvedUrl,String title,String description,String siteName,String imageUrl,String price,String currency,String contact,java.util.List<String> imageUrls) {
        this(resolvedUrl,title,description,siteName,imageUrl,price,currency,contact,imageUrls,imageUrls.size());
    }
    public PreviewMetadata(String resolvedUrl, String title, String description, String siteName,
            String imageUrl, String price, String currency, String contact) {
        this(resolvedUrl,title,description,siteName,imageUrl,price,currency,contact,
                imageUrl.isEmpty() ? java.util.Collections.emptyList() : java.util.Collections.singletonList(imageUrl),imageUrl.isEmpty()?0:1);
    }
}
