package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.UserStatus;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;

import java.util.Objects;
import java.util.UUID;

public class User {

    private final UUID id;
    private final String fullName;
    private final Email email;
    private final DocumentId documentId;
    private final UserRole role;
    private UserStatus status;

    public User(
            UUID id,
            String fullName,
            Email email,
            DocumentId documentId,
            UserRole role
    ) {
        if (id == null) {
            throw new IllegalArgumentException("El identificador del usuario es obligatorio.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio.");
        }
        if (email == null) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio.");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("El documento de identidad es obligatorio.");
        }
        if (role == null) {
            throw new IllegalArgumentException("El rol del usuario es obligatorio.");
        }

        this.id = id;
        this.fullName = fullName.trim();
        this.email = email;
        this.documentId = documentId;
        this.role = role;
        this.status = UserStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public Email getEmail() {
        return email;
    }

    public DocumentId getDocumentId() {
        return documentId;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof User user)) return false;
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
