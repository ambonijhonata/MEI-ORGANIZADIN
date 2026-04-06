package com.api.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertNull(result.paymentType());
    }

    @Test
    void shouldParseClientAndSingleService() {
        var result = parser.parse("maria silva - corte");

        assertEquals("maria silva", result.clientName());
        assertEquals(1, result.serviceNames().size());
        assertEquals("corte", result.serviceNames().get(0));
        assertNull(result.paymentType());
    }

    @Test
    void shouldTreatEntireTitleAsServiceWhenNoSeparator() {
        var result = parser.parse("corte de cabelo");

        assertNull(result.clientName());
        assertFalse(result.hasClient());
        assertEquals(1, result.serviceNames().size());
        assertEquals("corte de cabelo", result.serviceNames().get(0));
        assertNull(result.paymentType());
    }

    @Test
    void shouldHandleNullTitle() {
        var result = parser.parse(null);

        assertNull(result.clientName());
        assertTrue(result.serviceNames().isEmpty());
        assertNull(result.paymentType());
    }

    @Test
    void shouldHandleBlankTitle() {
        var result = parser.parse("   ");

        assertNull(result.clientName());
        assertTrue(result.serviceNames().isEmpty());
        assertNull(result.paymentType());
    }

    @Test
    void shouldTrimClientAndServiceNames() {
        var result = parser.parse("  ana clara  -  sobrancelha  +  bu\u00E7o  ");

        assertEquals("ana clara", result.clientName());
        assertEquals(2, result.serviceNames().size());
        assertEquals("sobrancelha", result.serviceNames().get(0));
        assertEquals("bu\u00E7o", result.serviceNames().get(1));
        assertNull(result.paymentType());
    }

    @Test
    void shouldIgnoreEmptyServiceTokens() {
        var result = parser.parse("ana - corte +  + barba ++ ");

        assertEquals("ana", result.clientName());
        assertEquals(2, result.serviceNames().size());
        assertEquals("corte", result.serviceNames().get(0));
        assertEquals("barba", result.serviceNames().get(1));
        assertNull(result.paymentType());
    }

    @Test
    void shouldAllowClientWithNoServiceTokens() {
        var result = parser.parse("ana -   ");

        assertEquals("ana", result.clientName());
        assertTrue(result.serviceNames().isEmpty());
        assertNull(result.paymentType());
    }

    @Test
    void shouldParseWithoutSpacesAroundSeparators() {
        var result = parser.parse("jhonata-sobrancelha+buco");

        assertEquals("jhonata", result.clientName());
        assertEquals(List.of("sobrancelha", "buco"), result.serviceNames());
        assertNull(result.paymentType());
    }

    @Test
    void shouldParseWithMixedSeparatorSpacing() {
        var result = parser.parse("jhonata -sobrancelha+ buco +henna");

        assertEquals("jhonata", result.clientName());
        assertEquals(List.of("sobrancelha", "buco", "henna"), result.serviceNames());
        assertNull(result.paymentType());
    }

    @Test
    void shouldExtractKnownPaymentTypeFromSuffix() {
        var result = parser.parse("jhonata - sobrancelha + buco (pix)");

        assertEquals("jhonata", result.clientName());
        assertEquals(List.of("sobrancelha", "buco"), result.serviceNames());
        assertEquals(PaymentType.PIX, result.paymentType());
    }

    @Test
    void shouldNormalizeAccentedPaymentTypeFromSuffix() {
        var debitResult = parser.parse("maria - corte (d\u00E9bito)");
        var creditResult = parser.parse("maria - corte (cr\u00E9dito)");

        assertEquals(PaymentType.DEBITO, debitResult.paymentType());
        assertEquals(PaymentType.CREDITO, creditResult.paymentType());
    }

    @Test
    void shouldIgnoreUnknownPaymentTypeButKeepServiceParsing() {
        var result = parser.parse("maria - corte + barba (boleto)");

        assertEquals("maria", result.clientName());
        assertEquals(List.of("corte", "barba"), result.serviceNames());
        assertNull(result.paymentType());
    }
}
