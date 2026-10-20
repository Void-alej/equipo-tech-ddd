package com.nexusmarket.domain;

import com.nexusmarket.application.services.UserService;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.DuplicateUserException;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private InMemoryUserRepository repository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        userService = new UserService(repository);
    }

    @Test
    void shouldRegisterBuyer() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "LAURA@example.com",
                "CC12345"
        );

        assertEquals(UserRole.BUYER, buyer.getRole());
        assertTrue(buyer.isActive());
        assertEquals("laura@example.com", buyer.getEmail().getValue());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        userService.registerBuyer("Laura Pérez", "laura@example.com", "CC12345");

        assertThrows(
                DuplicateUserException.class,
                () -> userService.registerBuyer(
                        "Carlos Gómez",
                        "LAURA@example.com",
                        "CC67890"
                )
        );
    }

    @Test
    void shouldRejectDuplicateDocument() {
        userService.registerBuyer("Laura Pérez", "laura@example.com", "CC12345");

        assertThrows(
                DuplicateUserException.class,
                () -> userService.registerBuyer(
                        "Carlos Gómez",
                        "carlos@example.com",
                        "cc12345"
                )
        );
    }

    @Test
    void shouldAllowAdministratorToRegisterSeller() {
        User administrator = userService.registerBuyer(
                "Admin temporal",
                "admin@example.com",
                "CC10001"
        );

        // Para esta prueba se construye un administrador explícitamente.
        User admin = new User(
                java.util.UUID.randomUUID(),
                "Administrador",
                new com.nexusmarket.domain.valueobjects.Email("admin2@example.com"),
                new com.nexusmarket.domain.valueobjects.DocumentId("CC10002"),
                UserRole.ADMINISTRATOR
        );
        repository.save(admin);

        User seller = userService.registerUserByAdministrator(
                admin,
                "Tienda Central",
                "seller@example.com",
                "NIT12345",
                UserRole.SELLER
        );

        assertEquals(UserRole.SELLER, seller.getRole());
        assertTrue(seller.isActive());
    }

    @Test
    void shouldRejectRegistrationByNonAdministrator() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> userService.registerUserByAdministrator(
                        buyer,
                        "Tienda Central",
                        "seller@example.com",
                        "NIT12345",
                        UserRole.SELLER
                )
        );
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerBuyer(
                        "   ",
                        "laura@example.com",
                        "CC12345"
                )
        );
    }
}
