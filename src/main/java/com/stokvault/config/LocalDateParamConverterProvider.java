package com.stokvault.config;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Lets resource methods take dates from the URL, e.g. ?from=2026-06-01. JAX-RS can only convert
 * text to types with a valueOf/fromString method or a String constructor, which LocalDate lacks.
 */
@Provider
public class LocalDateParamConverterProvider implements ParamConverterProvider {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        if (rawType != LocalDate.class) {
            return null;
        }
        return (ParamConverter<T>) new ParamConverter<LocalDate>() {
            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(value);
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("Invalid date '" + value + "', expected yyyy-mm-dd");
                }
            }

            @Override
            public String toString(LocalDate value) {
                return value.toString();
            }
        };
    }
}
