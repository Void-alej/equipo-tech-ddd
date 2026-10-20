package com.nexusmarket.infrastructure.repositories;

import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryUserRepository implements UserRepository {

    private final ConcurrentMap<UUID, User> users = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        if (user == null) {
            throw new IllegalArgumentException("El usuario no puede ser nulo.");
        }
        users.put(user.getId(), user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public boolean existsByEmail(Email email) {
        return users.values().stream()
                .anyMatch(user -> user.getEmail().equals(email));
    }

    @Override
    public boolean existsByDocumentId(DocumentId documentId) {
        return users.values().stream()
                .anyMatch(user -> user.getDocumentId().equals(documentId));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }
}
