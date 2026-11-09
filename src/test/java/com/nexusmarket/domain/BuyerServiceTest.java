package com.nexusmarket.domain;

import com.nexusmarket.application.services.BuyerService;
import com.nexusmarket.application.services.UserService;
import com.nexusmarket.domain.enums.BuyerCommercialStatus;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.BuyerProfile;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.valueobjects.DocumentId;
import com.nexusmarket.domain.valueobjects.Email;
import com.nexusmarket.infrastructure.repositories.InMemoryBuyerProfileRepository;
import com.nexusmarket.infrastructure.repositories.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BuyerServiceTest {

    private InMemoryUserRepository userRepository;
    private InMemoryBuyerProfileRepository profileRepository;
    private UserService userService;
    private BuyerService buyerService;
    private User admin;
    private User buyer;
    private User anotherBuyer;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        profileRepository = new InMemoryBuyerProfileRepository();

        userService = new UserService(userRepository);
        buyerService = new BuyerService(userRepository, profileRepository);

        admin = new User(
                UUID.randomUUID(),
                "Administrador",
                new Email("admin@example.com"),
                new DocumentId("CC10001"),
                UserRole.ADMINISTRATOR
        );
        userRepository.save(admin);

        buyer = userService.registerBuyer(
                "Laura Pérez",
                "laura@example.com",
                "CC12345"
        );

        anotherBuyer = userService.registerBuyer(
                "Carlos Gómez",
                "carlos@example.com",
                "CC67890"
        );
    }

    @Test
    void shouldCreateBuyerProfileWithActiveCommercialStatus() {
        BuyerProfile profile = buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of("Carrera 40 # 15-20")
        );

        assertEquals(buyer.getId(), profile.getBuyerId());
        assertEquals("Calle 10 # 20-30", profile.getPrimaryAddress());
        assertEquals(List.of("Carrera 40 # 15-20"),
                profile.getAdditionalAddresses());
        assertEquals(BuyerCommercialStatus.ACTIVE,
                profile.getCommercialStatus());
        assertTrue(profile.canMakePurchases());
    }

    @Test
    void shouldCreateProfileWithoutAdditionalAddresses() {
        BuyerProfile profile = buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                null
        );

        assertTrue(profile.getAdditionalAddresses().isEmpty());
    }

    @Test
    void shouldRejectProfileWithoutPrimaryAddress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> buyerService.createMyProfile(buyer, " ", List.of())
        );
    }

    @Test
    void shouldRejectDuplicateBuyerProfile() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> buyerService.createMyProfile(
                        buyer,
                        "Otra dirección",
                        List.of()
                )
        );
    }

    @Test
    void shouldRejectProfileCreationByNonBuyer() {
        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> buyerService.createMyProfile(
                        admin,
                        "Calle 10 # 20-30",
                        List.of()
                )
        );
    }

    @Test
    void shouldGetOwnBuyerProfile() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        BuyerProfile profile = buyerService.getMyProfile(buyer);

        assertEquals(buyer.getId(), profile.getBuyerId());
    }

    @Test
    void shouldNotAllowBuyerToReadAnotherBuyerProfile() {
        buyerService.createMyProfile(
                anotherBuyer,
                "Carrera 50 # 30-40",
                List.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> buyerService.getMyProfile(buyer)
        );
    }

    @Test
    void shouldUpdateOwnAddresses() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        BuyerProfile updated = buyerService.updateMyAddresses(
                buyer,
                "Carrera 15 # 25-35",
                List.of("Calle 80 # 10-20")
        );

        assertEquals("Carrera 15 # 25-35", updated.getPrimaryAddress());
        assertEquals(
                List.of("Calle 80 # 10-20"),
                updated.getAdditionalAddresses()
        );
    }

    @Test
    void shouldRejectUpdateWhenBuyerHasNoProfile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> buyerService.updateMyAddresses(
                        buyer,
                        "Calle 10 # 20-30",
                        List.of()
                )
        );
    }

    @Test
    void shouldAllowAdministratorToDeactivateBuyerCommercialStatus() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        BuyerProfile updated = buyerService.changeCommercialStatus(
                admin,
                buyer.getId(),
                BuyerCommercialStatus.INACTIVE
        );

        assertEquals(
                BuyerCommercialStatus.INACTIVE,
                updated.getCommercialStatus()
        );
        assertFalse(updated.canMakePurchases());
    }

    @Test
    void shouldAllowAdministratorToReactivateBuyerCommercialStatus() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        buyerService.changeCommercialStatus(
                admin,
                buyer.getId(),
                BuyerCommercialStatus.INACTIVE
        );

        BuyerProfile updated = buyerService.changeCommercialStatus(
                admin,
                buyer.getId(),
                BuyerCommercialStatus.ACTIVE
        );

        assertTrue(updated.canMakePurchases());
    }

    @Test
    void shouldRejectCommercialStatusChangeByBuyer() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> buyerService.changeCommercialStatus(
                        buyer,
                        buyer.getId(),
                        BuyerCommercialStatus.INACTIVE
                )
        );
    }

    @Test
    void shouldRejectCommercialStatusChangeForNonBuyer() {
        User seller = userService.registerUserByAdministrator(
                admin,
                "Tienda Central",
                "seller@example.com",
                "NIT12345",
                UserRole.SELLER
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> buyerService.changeCommercialStatus(
                        admin,
                        seller.getId(),
                        BuyerCommercialStatus.INACTIVE
                )
        );
    }

    @Test
    void shouldRejectOperationsBySuspendedBuyer() {
        buyerService.createMyProfile(
                buyer,
                "Calle 10 # 20-30",
                List.of()
        );

        userService.suspendUser(admin, buyer.getId());

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> buyerService.getMyProfile(buyer)
        );
    }

    @Test
    void shouldRejectOperationsByUnregisteredBuyer() {
        User unregisteredBuyer = new User(
                UUID.randomUUID(),
                "Usuario no registrado",
                new Email("unregistered@example.com"),
                new DocumentId("CC99999"),
                UserRole.BUYER
        );

        assertThrows(
                UnauthorizedUserOperationException.class,
                () -> buyerService.createMyProfile(
                        unregisteredBuyer,
                        "Calle 10 # 20-30",
                        List.of()
                )
        );
    }
}
