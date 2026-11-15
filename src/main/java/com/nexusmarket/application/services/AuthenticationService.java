package com.nexusmarket.application.services;

import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.UserRepository;
import com.nexusmarket.infrastructure.repositories.PersistentConcurrentMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Autenticación local para la demostración del marketplace.
 * Las contraseñas se guardan como hashes PBKDF2 con sal aleatoria;
 * los tokens de sesión son temporales y se invalidan al reiniciar.
 */
public final class AuthenticationService {

    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ConcurrentMap<UUID, Credential> credentials =
            PersistentConcurrentMap.create("credentials");
    private final ConcurrentMap<String, UUID> sessions =
            new ConcurrentHashMap<>();

    public AuthenticationService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("El repositorio de usuarios es obligatorio.");
        }
        this.userRepository = userRepository;
    }

    public void ensurePassword(User user, String password) {
        if (user == null) {
            throw new IllegalArgumentException("El usuario es obligatorio.");
        }
        credentials.computeIfAbsent(user.getId(), ignored -> createCredential(password));
    }

    public void setPassword(User user, String password) {
        if (user == null) {
            throw new IllegalArgumentException("El usuario es obligatorio.");
        }
        credentials.put(user.getId(), createCredential(password));
    }

    public Map<String, Object> login(String email, String password) {
        if (email == null || email.isBlank() || password == null) {
            throw new IllegalArgumentException("Correo y contraseña son obligatorios.");
        }

        User user = userRepository.findAll().stream()
                .filter(candidate -> candidate.getEmail().getValue()
                        .equalsIgnoreCase(email.trim()))
                .findFirst()
                .orElseThrow(this::invalidCredentials);

        Credential credential = credentials.get(user.getId());
        if (!user.isActive() || credential == null
                || !verify(password, credential)) {
            throw invalidCredentials();
        }

        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tokenBytes);
        sessions.put(token, user.getId());

        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "name", user.getFullName(),
                        "email", user.getEmail().getValue(),
                        "role", user.getRole(),
                        "status", user.getStatus()
                )
        );
    }

    public User requireSession(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedUserOperationException("Inicia sesión para continuar.");
        }
        UUID userId = sessions.get(token);
        if (userId == null) {
            throw new UnauthorizedUserOperationException("La sesión no es válida o expiró.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedUserOperationException(
                        "El usuario de la sesión ya no existe."));
        if (!user.isActive()) {
            sessions.remove(token);
            throw new UnauthorizedUserOperationException("El usuario está suspendido.");
        }
        return user;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private Credential createCredential(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "La contraseña debe contener al menos 8 caracteres.");
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new Credential(salt, hash(password, salt, ITERATIONS), ITERATIONS);
    }

    private boolean verify(String password, Credential credential) {
        return MessageDigest.isEqual(
                hash(password, credential.salt, credential.iterations),
                credential.passwordHash);
    }

    private byte[] hash(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(), salt, iterations, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo procesar la contraseña.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    private UnauthorizedUserOperationException invalidCredentials() {
        return new UnauthorizedUserOperationException(
                "Correo o contraseña incorrectos, o cuenta inactiva.");
    }

    private static final class Credential implements Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] salt;
        private final byte[] passwordHash;
        private final int iterations;

        private Credential(byte[] salt, byte[] passwordHash, int iterations) {
            this.salt = salt.clone();
            this.passwordHash = passwordHash.clone();
            this.iterations = iterations;
        }
    }
}
