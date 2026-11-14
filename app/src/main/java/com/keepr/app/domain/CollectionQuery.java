package com.keepr.app.domain;

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

/** Filters compose; price ordering never treats currencies as interchangeable. */
public final class CollectionQuery {
    public enum Sort { NEWEST, OLDEST, PRICE_LOW, PRICE_HIGH }
    private CollectionQuery() {}
    public static String source(SavedItem item) {
        try { String host=URI.create(item.url()).getHost(); if(host!=null) return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.",""); } catch(IllegalArgumentException ignored) { }
        return item.siteName().isEmpty()?"Shared content":item.siteName();
    }
    public static List<SavedItem> apply(List<SavedItem> items,String folder,String query,SavedItem.Status status,
            String tag,String source,long fromInclusive,long untilExclusive,Sort sort) {
        List<SavedItem> result=new ArrayList<>(); String needle=query.trim().toLowerCase(Locale.ROOT);
        for(SavedItem item:items) {
            if(!folder.equals("All") && !folder.equals(item.categoryId()==null?"Other":item.categoryId())) continue;
            if(status!=null && status!=item.status()) continue;
            if(!tag.isEmpty() && item.tags().stream().noneMatch(value->value.equalsIgnoreCase(tag))) continue;
            if(!source.isEmpty() && !source(item).equalsIgnoreCase(source)) continue;
            if(item.createdAt()<fromInclusive || item.createdAt()>=untilExclusive) continue;
            String text=String.join(" ",item.title(),item.url(),item.notes(),item.description(),item.sharedText(),String.join(" ",item.tags())).toLowerCase(Locale.ROOT);
            if(!text.contains(needle)) continue;
            result.add(item);
        }
        Comparator<SavedItem> newest=Comparator.comparingLong(SavedItem::createdAt).reversed().thenComparing(SavedItem::id);
        Comparator<SavedItem> order=switch(sort) {
            case NEWEST -> newest;
            case OLDEST -> Comparator.comparingLong(SavedItem::createdAt).thenComparing(SavedItem::id);
            case PRICE_LOW, PRICE_HIGH -> (a,b)->{
                BigDecimal ap=price(a),bp=price(b);
                if(ap==null || bp==null) return ap==bp?newest.compare(a,b):ap==null?1:-1;
                int currency=a.currency().compareToIgnoreCase(b.currency()); if(currency!=0) return currency;
                int amount=ap.compareTo(bp)*(sort==Sort.PRICE_HIGH?-1:1); return amount==0?newest.compare(a,b):amount;
            };
        };
        result.sort(order); return result;
    }
    private static BigDecimal price(SavedItem item) {
        String value=item.price().trim();
        if(!value.matches("[0-9]+(?:\\.[0-9]+)?")) return null;
        try { return new BigDecimal(value); } catch(NumberFormatException ignored) { return null; }
    }
}
