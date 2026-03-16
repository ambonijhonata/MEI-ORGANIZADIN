package com.api.servicecatalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceDescriptionNormalizerTest {

    private ServiceDescriptionNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ServiceDescriptionNormalizer();
    }

    @Test
    void shouldTrimWhitespace() {
        assertEquals("corte de cabelo", normalizer.normalize("  corte de cabelo  "));
    }

    @Test
    void shouldCollapseMultipleSpaces() {
        assertEquals("corte de cabelo", normalizer.normalize("corte   de   cabelo"));
    }

    @Test
    void shouldConvertToLowercase() {
        assertEquals("corte de cabelo", normalizer.normalize("Corte De Cabelo"));
    }

    @Test
    void shouldRemoveAccents() {
        assertEquals("manicure e pedicure", normalizer.normalize("Manicure e Pedicure"));
        assertEquals("depilacao", normalizer.normalize("Depilação"));
        assertEquals("coloracao", normalizer.normalize("Coloração"));
    }

    @Test
    void shouldHandleAllTransformationsTogether() {
        assertEquals("coloracao e corte", normalizer.normalize("  Coloração   e   CORTE  "));
    }

    @Test
    void shouldReturnEmptyForNull() {
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    void shouldReturnEmptyForBlank() {
        assertEquals("", normalizer.normalize("   "));
    }
}
