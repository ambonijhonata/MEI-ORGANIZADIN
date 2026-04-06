package com.api.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTitleParserTest {

    private EventTitleParser parser;

    @BeforeEach
    void setUp() {
        parser = new EventTitleParser();
    }

    @Test
    void shouldParseClientAndMultipleServices() {
        var result = parser.parse("fabiane honorato - sobrancelha + bu\u00E7o + henna + rosto");

        assertEquals("fabiane honorato", result.clientName());
        assertEquals(4, result.serviceNames().size());
        assertEquals("sobrancelha", result.serviceNames().get(0));
        assertEquals("bu\u00E7o", result.serviceNames().get(1));
        assertEquals("henna", result.serviceNames().get(2));
        assertEquals("rosto", result.serviceNames().get(3));
        assertTrue(result.hasClient());
    }

    @Test
    void shouldParseClientAndSingleService() {
        var result = parser.parse("maria silva - corte");

        assertEquals("maria silva", result.clientName());
        assertEquals(1, result.serviceNames().size());
        assertEquals("corte", result.serviceNames().get(0));
    }

    @Test
    void shouldTreatEntireTitleAsServiceWhenNoSeparator() {
        var result = parser.parse("corte de cabelo");

        assertNull(result.clientName());
        assertFalse(result.hasClient());
        assertEquals(1, result.serviceNames().size());
        assertEquals("corte de cabelo", result.serviceNames().get(0));
    }

    @Test
    void shouldHandleNullTitle() {
        var result = parser.parse(null);

        assertNull(result.clientName());
        assertTrue(result.serviceNames().isEmpty());
    }

    @Test
    void shouldHandleBlankTitle() {
        var result = parser.parse("   ");

        assertNull(result.clientName());
        assertTrue(result.serviceNames().isEmpty());
    }

    @Test
    void shouldTrimClientAndServiceNames() {
        var result = parser.parse("  ana clara  -  sobrancelha  +  bu\u00E7o  ");

        assertEquals("ana clara", result.clientName());
        assertEquals(2, result.serviceNames().size());
        assertEquals("sobrancelha", result.serviceNames().get(0));
        assertEquals("bu\u00E7o", result.serviceNames().get(1));
    }

    @Test
    void shouldIgnoreEmptyServiceTokens() {
        var result = parser.parse("ana - corte +  + barba ++ ");

        assertEquals("ana", result.clientName());
        assertEquals(2, result.serviceNames().size());
        assertEquals("corte", result.serviceNames().get(0));
        assertEquals("barba", result.serviceNames().get(1));
    }

    @Test
    void shouldAllowClientWithNoServiceTokens() {
        var result = parser.parse("ana -   ");

        assertEquals("ana", result.clientName());
        assertTrue(result.serviceNames().isEmpty());
    }
}
