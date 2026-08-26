package net.paramada.pokemada.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ServerSettingsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void savesSessionWithoutPasswordOrEndpoint() throws Exception {
        Path file = temporaryDirectory.resolve("server.properties");
        new ServerSettings("localhost:8000", " ash ", " token ").save(file);

        ServerSettings loaded = ServerSettings.load(file);
        assertEquals(ServerSettings.DEFAULT_BASE_URL, loaded.baseUrl());
        assertEquals("ash", loaded.username());
        assertEquals("token", loaded.token());
        String persisted = java.nio.file.Files.readString(file).toLowerCase();
        assertFalse(persisted.contains("password"));
        assertFalse(persisted.contains("localhost"));
        assertFalse(persisted.contains("http"));
    }

    @Test
    void logoutKeepsServerAndUser() {
        ServerSettings loggedOut = new ServerSettings("http://localhost:8000", "misty", "secret").withoutToken();
        assertEquals("http://localhost:8000/", loggedOut.baseUrl());
        assertEquals("misty", loggedOut.username());
        assertEquals("", loggedOut.token());
    }
}
