package com.stokvault.web;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.ConverterException;
import jakarta.faces.convert.FacesConverter;

import java.util.UUID;

/**
 * Converts UUIDs to and from text in pages (URL parameters, drop-down values).
 * forClass: Faces uses it automatically for every UUID-typed property.
 */
@FacesConverter(forClass = UUID.class)
public class UuidConverter implements Converter<UUID> {

    @Override
    public UUID getAsObject(FacesContext context, UIComponent component, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new ConverterException("Not a valid id");
        }
    }

    @Override
    public String getAsString(FacesContext context, UIComponent component, UUID value) {
        return value == null ? "" : value.toString();
    }
}
