package com.api.client;

import com.api.user.ApplicationUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientTest {

    private final ApplicationUser user = new ApplicationUser("sub", "email@test.com", "Name");

    @Test
    void shouldCreateClientWithRequiredFields() {
        Client client = new Client(user, "Maria Silva", "maria silva");

        assertEquals("Maria Silva", client.getName());
        assertEquals("maria silva", client.getNormalizedName());
        assertEquals(user, client.getUser());
        assertNull(client.getCpf());
        assertNull(client.getDateOfBirth());
        assertNull(client.getEmail());
        assertNull(client.getPhone());
    }

    @Test
    void shouldSetAllOptionalFields() {
        Client client = new Client(user, "Maria", "maria");

        client.updatePersonalInfo(
                "12345678901",
                LocalDate.of(1990, 5, 15),
                "maria@test.com",
                "11999999999"
        );

        assertEquals("12345678901", client.getCpf());
        assertEquals(LocalDate.of(1990, 5, 15), client.getDateOfBirth());
        assertEquals("maria@test.com", client.getEmail());
        assertEquals("11999999999", client.getPhone());
    }

    @Test
    void shouldUpdateNameAndNormalizedName() {
        Client client = new Client(user, "Old", "old");

        client.rename("New", "new");

        assertEquals("New", client.getName());
        assertEquals("new", client.getNormalizedName());
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        Client client = new Client(user, "Maria", "maria");

        invokeLifecycleMethod(client, "prePersist");

        assertNotNull(client.getCreatedAt());
        assertNotNull(client.getUpdatedAt());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() throws InterruptedException {
        Client client = new Client(user, "Maria", "maria");
        invokeLifecycleMethod(client, "prePersist");
        var createdAt = client.getCreatedAt();

        Thread.sleep(10);
        invokeLifecycleMethod(client, "preUpdate");

        assertEquals(createdAt, client.getCreatedAt());
        assertTrue(client.getUpdatedAt().isAfter(createdAt));
    }

    @Test
    void shouldReportWhenClientHasContactInfo() {
        Client client = new Client(user, "Maria", "maria");

        assertFalse(client.hasContactInfo());

        client.updatePersonalInfo("12345678901", null, null, null);

        assertTrue(client.hasContactInfo());
    }

    @Test
    void shouldEvaluateOwnershipForMissingAndMatchingUsers() {
        Client withoutUser = new Client(null, "Maria", "maria");
        assertFalse(withoutUser.belongsTo(1L));

        ApplicationUser userWithoutId = mock(ApplicationUser.class);
        Client withoutUserId = new Client(userWithoutId, "Maria", "maria");
        assertFalse(withoutUserId.belongsTo(1L));

        ApplicationUser identifiedUser = mock(ApplicationUser.class);
        when(identifiedUser.getId()).thenReturn(1L);
        Client ownedClient = new Client(identifiedUser, "Maria", "maria");
        assertTrue(ownedClient.belongsTo(1L));
        assertFalse(ownedClient.belongsTo(2L));
        assertFalse(ownedClient.belongsTo(null));
    }

    @Test
    void shouldEvaluateEveryContactInfoField() {
        assertTrue(clientWithContact("123", null, null, null).hasContactInfo());
        assertTrue(clientWithContact(null, LocalDate.of(1990, 1, 1), null, null).hasContactInfo());
        assertTrue(clientWithContact(null, null, "maria@test.com", null).hasContactInfo());
        assertTrue(clientWithContact(null, null, null, "11999999999").hasContactInfo());
    }

    @Test
    void shouldEvaluateNormalizedName() {
        assertFalse(new Client(user, "Maria", null).hasNormalizedName("maria"));

        Client client = new Client(user, "Maria", "maria");
        assertTrue(client.hasNormalizedName("maria"));
        assertFalse(client.hasNormalizedName("ana"));
    }

    private Client clientWithContact(
            final String cpf,
            final LocalDate dateOfBirth,
            final String email,
            final String phone) {
        Client client = new Client(user, "Maria", "maria");
        client.updatePersonalInfo(cpf, dateOfBirth, email, phone);
        return client;
    }

    private static void invokeLifecycleMethod(final Client client, final String methodName) {
        try {
            Method lifecycleMethod = Client.class.getDeclaredMethod(methodName);
            lifecycleMethod.setAccessible(true);
            lifecycleMethod.invoke(client);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            fail(exception);
        }
    }
}
