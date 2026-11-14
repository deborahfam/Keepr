package com.keepr.app.domain;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class TagNamesTest {
    @Test public void trimsDeduplicatesAndRemovesHashes() {
        assertEquals(List.of("Apartment","wishlist"),TagNames.parse(" #Apartment, apartment, ,##wishlist"));
    }
    @Test public void rejectsTooManyAndTooLongTags() {
        try { TagNames.parse("x".repeat(41)); fail(); } catch (IllegalArgumentException expected) { }
        try { TagNames.parse(java.util.stream.IntStream.range(0,21).mapToObj(i->"tag"+i).collect(java.util.stream.Collectors.joining(","))); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
