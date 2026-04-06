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
        assertEquals("depilacao", normalizer.normalize("Depila\u00E7\u00E3o"));
        assertEquals("coloracao", normalizer.normalize("Colora\u00E7\u00E3o"));
    }

    @Test
    void shouldHandleAllTransformationsTogether() {
        assertEquals("coloracao e corte", normalizer.normalize("  Colora\u00E7\u00E3o   e   CORTE  "));
    }

    @Test
    void shouldCollapseTabAndLineBreakWhitespaceLikeRegex() {
        assertEquals("corte de cabelo", normalizer.normalize("corte\t\tde\ncabelo"));
    }

    @Test
    void shouldRemoveCombiningAccentMarks() {
        assertEquals("cafe", normalizer.normalize("Cafe\u0301"));
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
