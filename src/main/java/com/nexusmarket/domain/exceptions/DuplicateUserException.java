package com.nexusmarket.domain.exceptions;

public class DuplicateUserException extends NexusMarketException {

    public DuplicateUserException(String message) {
        super(message);
    }
}
