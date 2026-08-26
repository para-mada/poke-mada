package net.paramada.pokemada.game.assets;

import javafx.scene.image.Image;
import net.paramada.pokemada.platform.AppDirectories;
import net.paramada.pokemada.server.ServerClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Persistent, authenticated read-through cache for virtual-item sprites. */
public final class VirtualItemSpriteCache {
    private final Path cacheDirectory;
    private final ConcurrentMap<String, CompletableFuture<Optional<Image>>> memoryCache =
            new ConcurrentHashMap<>();

    public VirtualItemSpriteCache() {
        this(AppDirectories.cacheDirectory().resolve("virtual-items"));
    }

    VirtualItemSpriteCache(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public CompletableFuture<Optional<Image>> load(String url, String token, ServerClient client) {
        if (url == null || url.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
        return memoryCache.computeIfAbsent(url, ignored -> loadCached(url).thenCompose(cached ->
                cached.isPresent() ? CompletableFuture.completedFuture(cached)
                        : client.image(url, token).thenApply(bytes -> store(url, bytes))
                                .exceptionally(failure -> Optional.empty())));
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
                Optional<Image> result = image(Files.readAllBytes(file));
                if (result.isEmpty()) Files.deleteIfExists(file);
                return result;
            } catch (IOException unreadable) {
                return Optional.empty();
            }
        });
    }

    private Optional<Image> store(String url, byte[] bytes) {
        Optional<Image> result = image(bytes);
        if (result.isEmpty()) return result;
        Path temporary = null;
        try {
            Files.createDirectories(cacheDirectory);
            temporary = Files.createTempFile(cacheDirectory, "item-", ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, cachedFile(url), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // The in-memory image remains usable when the disk cache is unavailable.
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
        return result;
    }

    private static Optional<Image> image(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return Optional.empty();
        Image image = new Image(new ByteArrayInputStream(bytes));
        return image.isError() ? Optional.empty() : Optional.of(image);
    }
}
