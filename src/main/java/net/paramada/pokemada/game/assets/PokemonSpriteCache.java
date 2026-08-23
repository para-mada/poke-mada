package net.paramada.pokemada.game.assets;

import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Persistent, read-through cache for the PokeAPI repository's 2D sprites. */
public final class PokemonSpriteCache {
    private static final URI SPRITE_ROOT = URI.create(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);

    private final Path cacheDirectory;
    private final HttpClient httpClient;
    private final ConcurrentMap<Integer, CompletableFuture<Optional<Image>>> memoryCache =
            new ConcurrentHashMap<>();

    public PokemonSpriteCache() {
        this(Path.of(System.getProperty("user.home"), ".poke-mada", "cache", "sprites"));
    }

    PokemonSpriteCache(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<Optional<Image>> load(int dexNumber) {
        if (dexNumber <= 0) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return memoryCache.computeIfAbsent(dexNumber,
                ignored -> CompletableFuture.supplyAsync(() -> loadCachedOrDownload(dexNumber)));
    }

    public Path cachedFile(int dexNumber) {
        return cacheDirectory.resolve(dexNumber + ".png");
    }

    private Optional<Image> loadCachedOrDownload(int dexNumber) {
        Path cachedFile = cachedFile(dexNumber);
        try {
            if (Files.isRegularFile(cachedFile) && Files.size(cachedFile) > 0) {
                return readImage(cachedFile);
            }

            HttpRequest request = HttpRequest.newBuilder(SPRITE_ROOT.resolve(dexNumber + ".png"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "PokeMada/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length == 0) {
                return Optional.empty();
            }

            Image image = new Image(new ByteArrayInputStream(response.body()));
            if (image.isError()) {
                return Optional.empty();
            }

            Files.createDirectories(cacheDirectory);
            Path temporaryFile = Files.createTempFile(cacheDirectory, dexNumber + "-", ".tmp");
            try {
                Files.write(temporaryFile, response.body());
                try {
                    Files.move(temporaryFile, cachedFile,
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporaryFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
            return Optional.of(image);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private static Optional<Image> readImage(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            Image image = new Image(input);
            if (image.isError()) {
                Files.deleteIfExists(file);
                return Optional.empty();
            }
            return Optional.of(image);
        }
    }
}
