package net.paramada.pokemada.game.assets;

import net.paramada.pokemada.platform.AppDirectories;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;

/** Conditional downloader for the canonical game catalog. The last valid file is always usable offline. */
public final class GameDataCatalogSync {
    private static final System.Logger LOGGER = System.getLogger(GameDataCatalogSync.class.getName());
    private static final Path DIRECTORY = AppDirectories.cacheDirectory().resolve("game-data");
    private static final Path CATALOG = DIRECTORY.resolve("catalog.json");
    private static final Path ETAG = DIRECTORY.resolve("catalog.etag");

    private GameDataCatalogSync() { }

    public static CompletableFuture<Boolean> synchronize(ServerSettings settings) {
        boolean hasCachedCatalog = loadCached();
        if (settings == null || settings.token().isBlank()) return CompletableFuture.completedFuture(false);
        String etag = hasCachedCatalog ? read(ETAG) : "";
        return new ServerClient(settings.baseUrl()).gameDataCatalog(settings.token(), etag)
                .thenApply(response -> {
                    if (!response.modified()) return false;
                    verifyVersion(response.body(), response.etag());
                    GameDataCatalog.install(response.body());
                    persist(response.body(), response.etag());
                    return true;
                }).exceptionally(failure -> {
                    LOGGER.log(System.Logger.Level.WARNING, "Could not refresh game-data catalog", failure);
                    return false;
                });
    }

    private static void verifyVersion(String body, String etag) {
        String expected = etag == null ? "" : etag.replace("\"", "").trim();
        if (expected.length() != 64) throw new IllegalArgumentException("Invalid catalog ETag");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder actual = new StringBuilder(64);
            for (byte value : digest) actual.append("%02x".formatted(Byte.toUnsignedInt(value)));
            if (!actual.toString().equalsIgnoreCase(expected)) {
                throw new IllegalArgumentException("Catalog checksum does not match ETag");
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static boolean loadCached() {
        String cached = read(CATALOG);
        if (cached.isBlank()) return false;
        try {
            GameDataCatalog.install(cached);
            return true;
        } catch (RuntimeException invalid) {
            LOGGER.log(System.Logger.Level.WARNING, "Ignoring invalid cached game-data catalog", invalid);
            return false;
        }
    }

    private static void persist(String body, String etag) {
        try {
            Files.createDirectories(DIRECTORY);
            Path temporary = Files.createTempFile(DIRECTORY, "catalog-", ".tmp");
            Files.writeString(temporary, body, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, CATALOG, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, CATALOG, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(ETAG, etag == null ? "" : etag, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not persist game-data catalog", failure);
        }
    }

    private static String read(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException ignored) {
            return "";
        }
    }
}
