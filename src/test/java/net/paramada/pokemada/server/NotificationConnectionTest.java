package net.paramada.pokemada.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class NotificationConnectionTest {
    @Test
    void convertsHttpsApiUrlToLegacyDataSocket() {
        assertEquals("wss://example.test/base/ws/data/Mada",
                NotificationConnection.socketUri("https://example.test/base/", "Mada").toString());
    }

    @Test
    void derivesBrowserCompatibleOriginFromApiUrl() {
        assertEquals("https://example.test", NotificationConnection.origin("https://example.test/base/"));
        assertEquals("http://localhost:8000", NotificationConnection.origin("http://localhost:8000/"));
    }

    @Test
    void decodesNestedNotificationEnvelope() {
        NotificationConnection.Event event = NotificationConnection.decode(
                "{\"message\":\"{\\\"type\\\":\\\"alert-notification\\\",\\\"data\\\":\\\"Alerta nueva\\\"}\"}");

        assertEquals("alert-notification", event.type());
        assertEquals("Alerta nueva", event.message());
    }

    @Test
    void ignoresOtherDataEvents() {
        assertNull(NotificationConnection.decode(
                "{\"message\":\"{\\\"type\\\":\\\"pokemon_data\\\",\\\"data\\\":\\\"x\\\"}\"}"));
    }

    @Test
    void identifiesNonNotificationMessagesForLogging() {
        NotificationConnection.IncomingMessage message = NotificationConnection.decodeIncoming(
                "{\"message\":\"{\\\"type\\\":\\\"karma\\\",\\\"data\\\":5}\"}");

        assertEquals("karma", message.type());
        assertEquals(5L, message.data());
    }
}
