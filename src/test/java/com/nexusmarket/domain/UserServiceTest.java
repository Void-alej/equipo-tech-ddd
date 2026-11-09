package com.nexusmarket.domain;

import com.nexusmarket.application.services.UserService;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.DuplicateUserException;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private InMemoryUserRepository repository;
    private UserService userService;
    private User admin;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository();
        userService = new UserService(repository);

        admin = new User(
                UUID.randomUUID(),
                "Administrador",
                new Email("admin@example.com"),
                new DocumentId("CC10002"),
                UserRole.ADMINISTRATOR
        );
        repository.save(admin);
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
        assertEquals(2, repository.findAll().size());
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

    @Test
    void shouldGetAllUsersAsAdministrator() {
        userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        assertEquals(2, userService.getAllUsers(admin).size());
    }

    @Test
    void shouldGetUserByIdAsAdministrator() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        User result = userService.getUserById(admin, buyer.getId());

        assertEquals(buyer.getId(), result.getId());
        assertEquals("Laura Pérez", result.getFullName());
    }

    @Test
    void shouldUpdateUserProfile() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        User updated = userService.updateUserByAdministrator(
                admin,
                buyer.getId(),
                "Laura Gómez",
                "laura.gomez@example.com",
                "CC54321"
        );

        assertEquals("Laura Gómez", updated.getFullName());
        assertEquals("laura.gomez@example.com", updated.getEmail().getValue());
        assertEquals("CC54321", updated.getDocumentId().getValue());
        assertEquals(UserRole.BUYER, updated.getRole());
    }

    @Test
    void shouldRejectDuplicateEmailWhenUpdatingUser() {
        userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );
        User second = userService.registerBuyer(
                "Carlos Gómez",
                "carlos@example.com",
                "CC67890"
        );

        assertThrows(
                DuplicateUserException.class,
                () -> userService.updateUserByAdministrator(
                        admin,
                        second.getId(),
                        "Carlos Gómez",
                        "laura@example.com",
                        "CC67890"
                )
        );
    }

    @Test
    void shouldRejectDuplicateDocumentWhenUpdatingUser() {
        userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );
        User second = userService.registerBuyer(
                "Carlos Gómez",
                "carlos@example.com",
                "CC67890"
        );

        assertThrows(
                DuplicateUserException.class,
                () -> userService.updateUserByAdministrator(
                        admin,
                        second.getId(),
                        "Carlos Gómez",
                        "carlos@example.com",
                        "CC12345"
                )
        );
    }

    @Test
    void shouldSuspendUser() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        User suspended = userService.suspendUser(admin, buyer.getId());

        assertFalse(suspended.isActive());
    }

    @Test
    void shouldActivateSuspendedUser() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );
        userService.suspendUser(admin, buyer.getId());

        User activated = userService.activateUser(admin, buyer.getId());

        assertTrue(activated.isActive());
    }

    @Test
    void shouldRejectUserAdministrationByBuyer() {
        User buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> userService.suspendUser(buyer, buyer.getId())
        );
    }

    @Test
    void shouldRejectUserListingBySupervisor() {
        User supervisor = new User(
                UUID.randomUUID(),
                "Supervisor",
                new Email("supervisor@example.com"),
                new DocumentId("CC10003"),
                UserRole.SUPERVISOR
        );
        repository.save(supervisor);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> userService.getAllUsers(supervisor)
        );
    }
}
