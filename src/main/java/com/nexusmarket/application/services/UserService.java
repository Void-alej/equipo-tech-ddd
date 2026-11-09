package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.DuplicateUserException;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;

import java.util.List;
import java.util.UUID;

public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException(
                    "El repositorio de usuarios es obligatorio.");
        }
        this.userRepository = userRepository;
    }

    public User registerBuyer(
            String fullName, String email, String documentId) {
        return createUser(fullName, email, documentId, UserRole.BUYER);
    }

    public User registerUserByAdministrator(
            User actor,
            String fullName,
            String email,
            String documentId,
            UserRole role) {

        requireAdministrator(actor);

        if (role == null) {
            throw new IllegalArgumentException("El rol es obligatorio.");
        }

        return createUser(fullName, email, documentId, role);
    }

    /**
     * Consulta todos los usuarios.
     * Solo el administrador tiene permisos de lectura.
     */
    public List<User> getAllUsers(User actor) {
        requireReadPermission(actor);
        return List.copyOf(userRepository.findAll());
    }

    /**
     * Consulta un usuario mediante su identificador.
     */
    public User getUserById(User actor, UUID userId) {
        requireReadPermission(actor);
        return findUserOrThrow(userId);
    }

    /**
     * Actualiza el nombre, correo y documento de un usuario.
     * El identificador y el rol permanecen sin cambios.
     */
    public User updateUserByAdministrator(
            User actor,
            UUID userId,
            String fullName,
            String emailValue,
            String documentValue) {

        requireAdministrator(actor);

        User user = findUserOrThrow(userId);
        Email email = new Email(emailValue);
        DocumentId documentId = new DocumentId(documentValue);

        if (!user.getEmail().equals(email)
                && userRepository.existsByEmail(email)) {
            throw new DuplicateUserException(
                    "Ya existe un usuario con ese correo electrónico.");
        }

        if (!user.getDocumentId().equals(documentId)
                && userRepository.existsByDocumentId(documentId)) {
            throw new DuplicateUserException(
                    "Ya existe un usuario con ese documento de identidad.");
        }

        user.updateProfile(fullName, email, documentId);
        userRepository.save(user);

        return user;
    }

    /**
     * Suspende un usuario.
     */
    public User suspendUser(User actor, UUID userId) {
        requireAdministrator(actor);

        User user = findUserOrThrow(userId);
        user.suspend();
        userRepository.save(user);

        return user;
    }

    /**
     * Reactiva un usuario previamente suspendido.
     */
    public User activateUser(User actor, UUID userId) {
        requireAdministrator(actor);

        User user = findUserOrThrow(userId);
        user.activate();
        userRepository.save(user);

        return user;
    }

    private User createUser(
            String fullName,
            String emailValue,
            String documentValue,
            UserRole role) {

        Email email = new Email(emailValue);
        DocumentId documentId = new DocumentId(documentValue);

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateUserException(
                    "Ya existe un usuario con ese correo electrónico.");
        }

        if (userRepository.existsByDocumentId(documentId)) {
            throw new DuplicateUserException(
                    "Ya existe un usuario con ese documento de identidad.");
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

    private User findUserOrThrow(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "El identificador del usuario es obligatorio.");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el usuario indicado."));
    }

    private User requireAdministrator(User actor) {
        User registered = findActiveRegisteredUser(actor);

        if (registered.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo un administrador puede realizar esta operación.");
        }

        return registered;
    }

    private User requireReadPermission(User actor) {
        User registered = findActiveRegisteredUser(actor);

        if (registered.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "No tienes permisos para consultar la información de usuarios.");
        }

        return registered;
    }

    private User findActiveRegisteredUser(User actor) {
        if (actor == null || !actor.isActive()) {
            throw new UnauthorizedUserOperationException(
                    "Se requiere un usuario activo.");
        }

        User registered = userRepository.findById(actor.getId())
                .orElseThrow(() -> new UnauthorizedUserOperationException(
                        "El usuario no está registrado."));

        if (!registered.isActive()
                || registered.getRole() != actor.getRole()) {
            throw new UnauthorizedUserOperationException(
                    "El usuario no está activo o sus permisos no coinciden.");
        }

        return registered;
    }
}
