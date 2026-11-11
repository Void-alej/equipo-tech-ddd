package com.nexusmarket.domain;

import com.nexusmarket.application.exceptions.SellerOnboardingException;
import com.nexusmarket.application.services.SellerOnboardingService;
import com.nexusmarket.application.services.UserService;
import com.nexusmarket.application.services.WarehouseService;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.enums.WarehouseType;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.models.Warehouse;
import com.nexusmarket.domain.ports.out.WarehouseRepository;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryWarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SellerOnboardingServiceTest {

    private InMemoryUserRepository userRepository;
    private InMemoryWarehouseRepository warehouseRepository;
    private SellerOnboardingService onboardingService;
    private User administrator;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        warehouseRepository = new InMemoryWarehouseRepository();

        UserService userService = new UserService(userRepository);
        WarehouseService warehouseService = new WarehouseService(
                warehouseRepository,
                userRepository
        );

        onboardingService = new SellerOnboardingService(
                userService,
                warehouseService
        );

        administrator = new User(
                UUID.randomUUID(),
                "Administrador",
                new Email("admin@example.com"),
                new DocumentId("CC10001"),
                UserRole.ADMINISTRATOR
        );

        userRepository.save(administrator);
    }

    @Test
    void shouldRegisterSellerAndCreateFirstWarehouse() {
        var result = onboardingService.registerSellerWithFirstWarehouse(
                administrator,
                "Tienda Central",
                "seller@example.com",
                "NIT12345",
                "Bodega principal",
                "Medellín"
        );

        assertEquals(UserRole.SELLER, result.seller().getRole());
        assertEquals(result.seller().getId(),
                result.firstWarehouse().getSellerId());
        assertEquals(WarehouseType.SELLER,
                result.firstWarehouse().getType());

        assertTrue(userRepository.findById(result.seller().getId()).isPresent());
        assertTrue(warehouseRepository.findById(
                result.firstWarehouse().getId()).isPresent());
    }

    @Test
    void shouldKeepSellerRegisteredWhenWarehouseCreationFails() {
        RuntimeException storageFailure =
                new RuntimeException("Error simulado al guardar la bodega");

        WarehouseRepository failingRepository = new WarehouseRepository() {
            @Override
            public Warehouse save(Warehouse warehouse) {
                throw storageFailure;
            }

            @Override
            public Optional<Warehouse> findById(UUID id) {
                return Optional.empty();
            }

            @Override
            public List<Warehouse> findAll() {
                return List.of();
            }

            @Override
            public List<Warehouse> findBySellerId(UUID sellerId) {
                return List.of();
            }
        };

        WarehouseService failingWarehouseService = new WarehouseService(
                failingRepository,
                userRepository
        );

        SellerOnboardingService failingOnboardingService =
                new SellerOnboardingService(
                        new UserService(userRepository),
                        failingWarehouseService
                );

        SellerOnboardingException exception = assertThrows(
                SellerOnboardingException.class,
                () -> failingOnboardingService.registerSellerWithFirstWarehouse(
                        administrator,
                        "Tienda Central",
                        "seller@example.com",
                        "NIT12345",
                        "Bodega principal",
                        "Medellín"
                )
        );

        User registeredSeller = exception.getRegisteredSeller();

        assertEquals(UserRole.SELLER, registeredSeller.getRole());
        assertTrue(userRepository.findById(
                registeredSeller.getId()).isPresent());
        assertSame(storageFailure, exception.getCause());
    }

    @Test
    void shouldNotCreateWarehouseWhenSellerDataIsInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> onboardingService.registerSellerWithFirstWarehouse(
                        administrator,
                        "   ",
                        "seller@example.com",
                        "NIT12345",
                        "Bodega principal",
                        "Medellín"
                )
        );

        assertTrue(warehouseRepository.findAll().isEmpty());
        assertEquals(1, userRepository.findAll().size());
    }

    @Test
    void shouldRejectNonAdministratorWithoutCreatingSellerOrWarehouse() {
        User buyer = new User(
                UUID.randomUUID(),
                "Comprador",
                new Email("buyer@example.com"),
                new DocumentId("CC20002"),
                UserRole.BUYER
        );

        userRepository.save(buyer);

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> onboardingService.registerSellerWithFirstWarehouse(
                        buyer,
                        "Tienda Central",
                        "seller@example.com",
                        "NIT12345",
                        "Bodega principal",
                        "Medellín"
                )
        );

        assertEquals(2, userRepository.findAll().size());
        assertTrue(warehouseRepository.findAll().isEmpty());
    }
}
