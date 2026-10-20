package com.nexusmarket.domain.ports.out;

import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    void save(User user);

    Optional<User> findById(UUID id);

    boolean existsByEmail(Email email);

    boolean existsByDocumentId(DocumentId documentId);

    List<User> findAll();
}
