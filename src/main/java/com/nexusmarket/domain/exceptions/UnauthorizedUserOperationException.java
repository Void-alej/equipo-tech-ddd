package com.nexusmarket.domain.exceptions;

public class UnauthorizedUserOperationException extends NexusMarketException {

    public UnauthorizedUserOperationException(String message) {
        super(message);
    }
}
