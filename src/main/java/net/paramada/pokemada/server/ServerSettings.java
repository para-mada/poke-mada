package net.paramada.pokemada.server;

import net.paramada.pokemada.platform.AppDirectories;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Persistent connection settings. Passwords are deliberately never stored. */
public record ServerSettings(String baseUrl, String username, String token) {
    public static final String DEFAULT_BASE_URL = "https://pelis.paramada.ovh/";
    private static final String FILE_NAME = "server.properties";

    public ServerSettings {
        baseUrl = normalizeBaseUrl(baseUrl);
        username = username == null ? "" : username.trim();
        token = token == null ? "" : token.trim();
    }

    public static ServerSettings load() {
        return load(AppDirectories.dataDirectory().resolve(FILE_NAME));
    }

    static ServerSettings load(Path path) {
        if (!Files.isRegularFile(path)) return new ServerSettings(DEFAULT_BASE_URL, "", "");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            return new ServerSettings(DEFAULT_BASE_URL, properties.getProperty("username", ""),
                    properties.getProperty("token", ""));
        } catch (IOException unreadable) {
            return new ServerSettings(DEFAULT_BASE_URL, "", "");
        }
    }

    public void save() throws IOException {
        save(AppDirectories.dataDirectory().resolve(FILE_NAME));
    }

    void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Properties properties = new Properties();
        properties.setProperty("username", username);
        properties.setProperty("token", token);
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "PokeMada account session");
        }
    }

    public ServerSettings withoutToken() {
        return new ServerSettings(baseUrl, username, "");
    }

    static String normalizeBaseUrl(String value) {
        String url = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        if (!url.matches("(?i)^https?://.+")) url = "https://" + url;
        return url.endsWith("/") ? url : url + "/";
    }
}
