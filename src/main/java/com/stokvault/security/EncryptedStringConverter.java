package com.stokvault.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * A JPA AttributeConverter: JPA calls it on every save and load, so the entity field holds the
 * plain value while the database column only ever holds ciphertext.
 * Used as @Convert(converter = EncryptedStringConverter.class) on Member.nationalId.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return plaintext == null ? null : FieldCrypto.get().encrypt(plaintext);
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        return ciphertext == null ? null : FieldCrypto.get().decrypt(ciphertext);
    }
}
