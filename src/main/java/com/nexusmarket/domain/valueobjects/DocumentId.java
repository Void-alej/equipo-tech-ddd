package com.nexusmarket.domain.valueobjects;

import java.util.Locale;
import java.util.Objects;

public final class DocumentId {

    private final String value;

    public DocumentId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El documento de identidad es obligatorio.");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z0-9.-]{5,30}")) {
            throw new IllegalArgumentException(
                    "El documento debe tener entre 5 y 30 caracteres alfanuméricos."
            );
        }

        this.value = normalized;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof DocumentId documentId)) return false;
        return value.equals(documentId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
