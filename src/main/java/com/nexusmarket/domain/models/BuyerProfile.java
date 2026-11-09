package com.nexusmarket.domain.models;

import com.nexusmarket.domain.enums.BuyerCommercialStatus;

import java.util.List;
import java.util.UUID;

public class BuyerProfile {

    private final UUID buyerId;
    private String primaryAddress;
    private List<String> additionalAddresses;
    private BuyerCommercialStatus commercialStatus;

    public BuyerProfile(
            UUID buyerId,
            String primaryAddress,
            List<String> additionalAddresses,
            BuyerCommercialStatus commercialStatus) {

        if (buyerId == null) {
            throw new IllegalArgumentException(
                    "El identificador del comprador es obligatorio.");
        }

        this.buyerId = buyerId;
        updateAddresses(primaryAddress, additionalAddresses);

        if (commercialStatus == null) {
            throw new IllegalArgumentException(
                    "El estado comercial es obligatorio.");
        }

        this.commercialStatus = commercialStatus;
    }

    public void updateAddresses(
            String primaryAddress,
            List<String> additionalAddresses) {

        if (primaryAddress == null || primaryAddress.isBlank()) {
            throw new IllegalArgumentException(
                    "La dirección principal es obligatoria.");
        }

        if (additionalAddresses == null) {
            additionalAddresses = List.of();
        }

        if (additionalAddresses.stream().anyMatch(
                address -> address == null || address.isBlank())) {
            throw new IllegalArgumentException(
                    "Las direcciones adicionales no pueden estar vacías.");
        }

        this.primaryAddress = primaryAddress.trim();
        this.additionalAddresses = additionalAddresses.stream()
                .map(String::trim)
                .toList();
    }

    public void changeCommercialStatus(BuyerCommercialStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "El estado comercial es obligatorio.");
        }

        this.commercialStatus = status;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public String getPrimaryAddress() {
        return primaryAddress;
    }

    public List<String> getAdditionalAddresses() {
        return List.copyOf(additionalAddresses);
    }

    public BuyerCommercialStatus getCommercialStatus() {
        return commercialStatus;
    }

    public boolean canMakePurchases() {
        return commercialStatus == BuyerCommercialStatus.ACTIVE;
    }
}
