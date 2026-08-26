package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VirtualItemSpriteCacheTest {
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAValidSpriteFromDiskWithoutNetwork() throws Exception {
        VirtualItemSpriteCache cache = new VirtualItemSpriteCache(temporaryDirectory);
        String url = "https://example.test/media/virtual_items/super-protein.png";
        Files.write(cache.cachedFile(url), ONE_PIXEL_PNG);

        var image = cache.load(url, "unused", null).get(2, TimeUnit.SECONDS);

        assertTrue(image.isPresent());
        assertEquals(1, image.orElseThrow().getWidth());
    }

    @Test
    void fullUrlChangesTheCacheKey() {
        VirtualItemSpriteCache cache = new VirtualItemSpriteCache(temporaryDirectory);

        assertNotEquals(cache.cachedFile("https://example.test/item.png?v=1"),
                cache.cachedFile("https://example.test/item.png?v=2"));
    }

    @Test
    void ignoresMissingUrls() throws Exception {
        VirtualItemSpriteCache cache = new VirtualItemSpriteCache(temporaryDirectory);

        assertTrue(cache.load("", "unused", null).get(1, TimeUnit.SECONDS).isEmpty());
    }
}
