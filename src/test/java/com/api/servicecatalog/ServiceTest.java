package com.api.servicecatalog;

import com.api.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {

    private final User user = new User("sub", "email@test.com", "Name");

    @Test
    void shouldCreateServiceWithAllFields() {
        Service service = new Service(user, "Corte de Cabelo", "corte de cabelo", new BigDecimal("50.00"));

        assertEquals(user, service.getUser());
        assertEquals("Corte de Cabelo", service.getDescription());
        assertEquals("corte de cabelo", service.getNormalizedDescription());
        assertEquals(new BigDecimal("50.00"), service.getValue());
    }

    @Test
    void shouldUpdateFields() {
        Service service = new Service(user, "Old", "old", new BigDecimal("30.00"));

        service.setDescription("New");
        service.setNormalizedDescription("new");
        service.setValue(new BigDecimal("60.00"));

        assertEquals("New", service.getDescription());
        assertEquals("new", service.getNormalizedDescription());
        assertEquals(new BigDecimal("60.00"), service.getValue());
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        Service service = new Service(user, "Test", "test", new BigDecimal("10.00"));

        service.prePersist();

        assertNotNull(service.getCreatedAt());
        assertNotNull(service.getUpdatedAt());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() throws InterruptedException {
        Service service = new Service(user, "Test", "test", new BigDecimal("10.00"));
        service.prePersist();
        var createdAt = service.getCreatedAt();

        Thread.sleep(10);
        service.preUpdate();

        assertEquals(createdAt, service.getCreatedAt());
        assertTrue(service.getUpdatedAt().isAfter(createdAt));
    }
}
