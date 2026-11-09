package com.keepr.app.domain;

import java.util.List;
import java.util.function.Consumer;

/** All methods run on one serialized worker. Observers receive committed offline snapshots. */
public interface SavedItemRepository extends AutoCloseable {
    List<SavedItem> load() throws Exception;
    void add(SavedItem item);
    void delete(String id);
    void observe(Consumer<List<SavedItem>> observer);
    @Override void close();
}
