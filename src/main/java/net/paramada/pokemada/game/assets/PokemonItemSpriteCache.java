package net.paramada.pokemada.game.assets;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Persistent read-through cache for PokeAPI's item sprites. */
public final class PokemonItemSpriteCache {
    private static final URI ROOT = URI.create(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/");
    private static final Duration TIMEOUT = Duration.ofSeconds(12);
    private final Path directory = Path.of(System.getProperty("user.home"), ".poke-mada", "cache", "items");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ConcurrentMap<String, CompletableFuture<Optional<Image>>> memory = new ConcurrentHashMap<>();

    public CompletableFuture<Optional<Image>> load(String identifier) {
        if (identifier == null || !identifier.matches("[a-z0-9-]+")) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return memory.computeIfAbsent(identifier,
                ignored -> CompletableFuture.supplyAsync(() -> loadCachedOrDownload(identifier)));
    }

    private Optional<Image> loadCachedOrDownload(String identifier) {
        Path file = directory.resolve(identifier + ".png");
        try {
            if (Files.isRegularFile(file) && Files.size(file) > 0) {
                try (var input = Files.newInputStream(file)) {
                    Image image = new Image(input);
                    if (!image.isError()) return Optional.of(image);
                }
            }
            HttpRequest request = HttpRequest.newBuilder(ROOT.resolve(identifier + ".png"))
                    .timeout(TIMEOUT).header("User-Agent", "PokeMada/1.0").GET().build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) return Optional.empty();
            Image image = new Image(new ByteArrayInputStream(response.body()));
            if (image.isError()) return Optional.empty();
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, identifier + "-", ".tmp");
            try {
                Files.write(temporary, response.body());
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return Optional.of(image);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
