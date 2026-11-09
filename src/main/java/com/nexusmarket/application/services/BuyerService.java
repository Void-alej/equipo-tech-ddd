package com.nexusmarket.application.services;

import com.nexusmarket.domain.enums.BuyerCommercialStatus;
import com.nexusmarket.domain.enums.UserRole;
import com.nexusmarket.domain.exceptions.UnauthorizedUserOperationException;
import com.nexusmarket.domain.models.BuyerProfile;
import com.nexusmarket.domain.models.User;
import com.nexusmarket.domain.ports.out.BuyerProfileRepository;
import com.nexusmarket.domain.ports.out.UserRepository;

import java.util.List;
import java.util.UUID;

public class BuyerService {

    private final UserRepository userRepository;
    private final BuyerProfileRepository buyerProfileRepository;

    public BuyerService(
            UserRepository userRepository,
            BuyerProfileRepository buyerProfileRepository) {

        if (userRepository == null || buyerProfileRepository == null) {
            throw new IllegalArgumentException(
                    "Los repositorios de usuarios y compradores son obligatorios.");
        }

        this.userRepository = userRepository;
        this.buyerProfileRepository = buyerProfileRepository;
    }

    public BuyerProfile createMyProfile(
            User actor,
            String primaryAddress,
            List<String> additionalAddresses) {

        User buyer = requireBuyer(actor);

        if (buyerProfileRepository.findByBuyerId(buyer.getId()).isPresent()) {
            throw new IllegalArgumentException(
                    "El comprador ya tiene un perfil registrado.");
        }

        BuyerProfile profile = new BuyerProfile(
                buyer.getId(),
                primaryAddress,
                additionalAddresses,
                BuyerCommercialStatus.ACTIVE
        );

        buyerProfileRepository.save(profile);
        return profile;
    }

    public BuyerProfile getMyProfile(User actor) {
        User buyer = requireBuyer(actor);
        return findProfileOrThrow(buyer.getId());
    }

    public BuyerProfile updateMyAddresses(
            User actor,
            String primaryAddress,
            List<String> additionalAddresses) {

        User buyer = requireBuyer(actor);
        BuyerProfile profile = findProfileOrThrow(buyer.getId());

        profile.updateAddresses(primaryAddress, additionalAddresses);
        buyerProfileRepository.save(profile);

        return profile;
    }

    public BuyerProfile changeCommercialStatus(
            User actor,
            UUID buyerId,
            BuyerCommercialStatus status) {

        requireAdministrator(actor);

        if (buyerId == null) {
            throw new IllegalArgumentException(
                    "El identificador del comprador es obligatorio.");
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "El estado comercial es obligatorio.");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el comprador indicado."));

        if (buyer.getRole() != UserRole.BUYER) {
            throw new IllegalArgumentException(
                    "El usuario indicado no tiene el rol de comprador.");
        }

        BuyerProfile profile = findProfileOrThrow(buyerId);
        profile.changeCommercialStatus(status);
        buyerProfileRepository.save(profile);

        return profile;
    }

    private BuyerProfile findProfileOrThrow(UUID buyerId) {
        return buyerProfileRepository.findByBuyerId(buyerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El comprador no tiene un perfil registrado."));
    }

    private User requireBuyer(User actor) {
        User registered = findActiveRegisteredUser(actor);

        if (registered.getRole() != UserRole.BUYER) {
            throw new UnauthorizedUserOperationException(
                    "Solo un comprador puede realizar esta operación.");
        }

        return registered;
    }

    private User requireAdministrator(User actor) {
        User registered = findActiveRegisteredUser(actor);

        if (registered.getRole() != UserRole.ADMINISTRATOR) {
            throw new UnauthorizedUserOperationException(
                    "Solo un administrador puede cambiar el estado comercial.");
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
