package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.DuplicateUserException;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;

import java.util.UUID;

public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("El repositorio de usuarios es obligatorio.");
        }
        this.userRepository = userRepository;
    }

    public User registerBuyer(String fullName, String email, String documentId) {
        return createUser(fullName, email, documentId, UserRole.BUYER);
    }

    public User registerUserByAdministrator(
            User actor,
            String fullName,
            String email,
            String documentId,
            UserRole role
    ) {
        if (actor == null || !actor.isActive()
                || actor.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo un administrador activo puede registrar usuarios."
            );
        }

        if (role == null) {
            throw new IllegalArgumentException("El rol es obligatorio.");
        }

        return createUser(fullName, email, documentId, role);
    }

    private User createUser(
            String fullName,
            String emailValue,
            String documentValue,
            UserRole role
    ) {
        Email email = new Email(emailValue);
        DocumentId documentId = new DocumentId(documentValue);

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("Ya existe un usuario con ese correo electrónico.");
        }

        if (userRepository.existsByDocumentId(documentId)) {
            throw new DuplicateUserException("Ya existe un usuario con ese documento de identidad.");
        }

        User user = new User(
                UUID.randomUUID(),
                fullName,
                email,
                documentId,
                role
        );

        userRepository.save(user);
        return user;
    }
}
