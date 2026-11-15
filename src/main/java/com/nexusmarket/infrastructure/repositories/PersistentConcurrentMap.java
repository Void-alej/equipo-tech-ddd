package com.nexusmarket.infrastructure.repositories;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concurrent map opcionalmente respaldado en disco.
 *
 * <p>Las pruebas mantienen el comportamiento en memoria cuando no está
 * configurada la propiedad {@code nexusmarket.data.dir}. El servidor la
 * configura al iniciar para conservar el estado entre reinicios.</p>
 */
public final class PersistentConcurrentMap<K, V>
        extends ConcurrentHashMap<K, V> {

    private static final long serialVersionUID = 1L;
    private final Path file;

    private PersistentConcurrentMap(Path file) {
        this.file = file;
        load();
    }

    @SuppressWarnings("unchecked")
    public static <K, V>
            ConcurrentMap<K, V> create(String name) {
        String directory = System.getProperty("nexusmarket.data.dir");
        if (directory == null || directory.isBlank()) {
            return new ConcurrentHashMap<>();
        }
        return new PersistentConcurrentMap<>(
                Path.of(directory).resolve(name + ".bin"));
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (ObjectInputStream input =
                     new ObjectInputStream(Files.newInputStream(file))) {
            Object stored = input.readObject();
            if (!(stored instanceof Map<?, ?> map)) {
                throw new IllegalStateException(
                        "El archivo de persistencia no contiene un mapa: " + file);
            }
            map.forEach((key, value) -> super.put((K) key, (V) value));
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "No se pudo recuperar la persistencia " + file, exception);
        }
    }

    @Override
    public V put(K key, V value) {
        synchronized (this) {
            V previous = super.put(key, value);
            try {
                persist();
                return previous;
            } catch (RuntimeException exception) {
                if (previous == null) {
                    super.remove(key);
                } else {
                    super.put(key, previous);
                }
                throw exception;
            }
        }
    }

    @Override
    public V remove(Object key) {
        synchronized (this) {
            V previous = super.remove(key);
            if (previous != null) {
                try {
                    persist();
                } catch (RuntimeException exception) {
                    @SuppressWarnings("unchecked")
                    K restoredKey = (K) key;
                    super.put(restoredKey, previous);
                    throw exception;
                }
            }
            return previous;
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            Map<K, V> previous = new HashMap<>(this);
            super.clear();
            try {
                persist();
            } catch (RuntimeException exception) {
                super.putAll(previous);
                throw exception;
            }
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (ObjectOutputStream output =
                         new ObjectOutputStream(Files.newOutputStream(temporary))) {
                output.writeObject(new HashMap<>(this));
                output.flush();
            }
            try {
                Files.move(temporary, file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "No se pudo guardar la persistencia " + file, exception);
        }
    }
}
