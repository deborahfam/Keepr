package com.keepr.app.domain;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CollectionQueryTest {
    private SavedItem item(String id,String price,String currency,long saved) {
        return new SavedItem(id,"https://www.example.com/post","Apartment","Caption","Example","",price,currency,"","Note","Rentals",SavedItem.Status.ACTIVE,SavedItem.PreviewState.READY,"",saved,saved,"Original",List.of("Home"),List.of());
    }
    @Test public void filtersComposeAndDatesAreHalfOpen() {
        List<SavedItem> items=List.of(item("a","100","USD",100),item("b","200","USD",200));
        assertEquals("a",CollectionQuery.apply(items,"Rentals","caption",SavedItem.Status.ACTIVE,"home","example.com",100,200,CollectionQuery.Sort.NEWEST).get(0).id());
        assertTrue(CollectionQuery.apply(items,"Other","",null,"","",0,300,CollectionQuery.Sort.NEWEST).isEmpty());
        assertTrue(CollectionQuery.apply(items,"All","",SavedItem.Status.PURCHASED,"","",0,300,CollectionQuery.Sort.NEWEST).isEmpty());
        assertTrue(CollectionQuery.apply(items,"All","",null,"missing","",0,300,CollectionQuery.Sort.NEWEST).isEmpty());
        assertTrue(CollectionQuery.apply(items,"All","",null,"","elsewhere.com",0,300,CollectionQuery.Sort.NEWEST).isEmpty());
    }
    @Test public void pricesUseExactDecimalsGroupCurrenciesAndKeepMissingLast() {
        List<SavedItem> items=List.of(item("high","100.01","USD",1),item("low","9.99","USD",2),item("eur","999","EUR",3),item("missing","","USD",4),item("formatted","$2,000","USD",5));
        assertEquals(List.of("eur","low","high","formatted","missing"),ids(items,CollectionQuery.Sort.PRICE_LOW));
        assertEquals(List.of("eur","high","low","formatted","missing"),ids(items,CollectionQuery.Sort.PRICE_HIGH));
        assertEquals(List.of("formatted","missing","eur","low","high"),ids(items,CollectionQuery.Sort.NEWEST));
        assertEquals(List.of("high","low","eur","missing","formatted"),ids(items,CollectionQuery.Sort.OLDEST));
    }
    private List<String> ids(List<SavedItem> items,CollectionQuery.Sort sort) {
        List<String> ids=new ArrayList<>(); for(SavedItem item:CollectionQuery.apply(items,"All","",null,"","",Long.MIN_VALUE,Long.MAX_VALUE,sort)) ids.add(item.id()); return ids;
    }
}
