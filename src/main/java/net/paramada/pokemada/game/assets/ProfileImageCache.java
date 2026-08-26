package net.paramada.pokemada.game.assets;

import javafx.scene.image.Image;
import net.paramada.pokemada.platform.AppDirectories;
import net.paramada.pokemada.server.ServerClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Persistent read-through cache for account profile pictures. */
public final class ProfileImageCache {
    private final Path cacheDirectory;
    private final ConcurrentMap<String, CompletableFuture<Optional<Image>>> memoryCache = new ConcurrentHashMap<>();

    public ProfileImageCache() {
        this(AppDirectories.cacheDirectory().resolve("profiles"));
    }

    ProfileImageCache(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public CompletableFuture<Optional<Image>> load(String url, String token, ServerClient client) {
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
        return memoryCache.computeIfAbsent(url, ignored -> loadCached(url).thenCompose(cached ->
                cached.isPresent() ? CompletableFuture.completedFuture(cached)
                        : client.image(url, token).thenApply(bytes -> store(url, bytes))
                                .exceptionally(failure -> Optional.empty())));
    }

    public CompletableFuture<Optional<Image>> refresh(String url, String token, ServerClient client) {
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
        memoryCache.remove(url);
        return CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(cachedFile(url));
            } catch (IOException ignored) {
                // A locked cache file must not prevent the profile refresh.
            }
        }).thenCompose(ignored -> load(url, token, client));
    }

    Path cachedFile(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            return cacheDirectory.resolve(HexFormat.of().formatHex(digest) + ".img");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private CompletableFuture<Optional<Image>> loadCached(String url) {
        return CompletableFuture.supplyAsync(() -> {
            Path file = cachedFile(url);
            if (!Files.isRegularFile(file)) return Optional.empty();
            try {
                return image(Files.readAllBytes(file));
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        });
    }

    private Optional<Image> store(String url, byte[] bytes) {
        Optional<Image> image = image(bytes);
        if (image.isEmpty()) return image;
        try {
            Files.createDirectories(cacheDirectory);
            Files.write(cachedFile(url), bytes);
        } catch (IOException ignored) {
            // The downloaded image remains usable even when the disk cache is unavailable.
        }
        return image;
    }

    private static Optional<Image> image(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Optional.empty();
        Image image = new Image(new ByteArrayInputStream(bytes));
        return image.isError() ? Optional.empty() : Optional.of(image);
    }
}
