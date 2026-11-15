package com.nexusmarket.infrastructure.repositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

class PersistentConcurrentMapTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRestoreSavedEntriesAfterRecreatingTheMap() {
        String previousDirectory = System.getProperty("nexusmarket.data.dir");
        try {
            System.setProperty("nexusmarket.data.dir", temporaryDirectory.toString());

            ConcurrentMap<String, String> first =
                    PersistentConcurrentMap.create("sample");
            first.put("buyer-1", "ACTIVE");
            first.put("buyer-2", "INACTIVE");

            ConcurrentMap<String, String> restored =
                    PersistentConcurrentMap.create("sample");

            assertEquals("ACTIVE", restored.get("buyer-1"));
            assertEquals("INACTIVE", restored.get("buyer-2"));
            assertEquals(2, restored.size());
        } finally {
            restoreProperty(previousDirectory);
        }
    }

    @Test
    void shouldPersistUpdatesAndRemovals() {
        String previousDirectory = System.getProperty("nexusmarket.data.dir");
        try {
            System.setProperty("nexusmarket.data.dir", temporaryDirectory.toString());

            ConcurrentMap<String, String> first =
                    PersistentConcurrentMap.create("sample");
            first.put("order-1", "PAID");
            first.put("order-1", "DELIVERED");
            first.remove("order-1");

            ConcurrentMap<String, String> restored =
                    PersistentConcurrentMap.create("sample");

            assertTrue(restored.isEmpty());
        } finally {
            restoreProperty(previousDirectory);
        }
    }

    private void restoreProperty(String previousDirectory) {
        if (previousDirectory == null) {
            System.clearProperty("nexusmarket.data.dir");
        } else {
            System.setProperty("nexusmarket.data.dir", previousDirectory);
        }
    }
}
