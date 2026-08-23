package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonSpriteCacheTest {
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsAnExistingSpriteWithoutNeedingTheNetwork() throws Exception {
        PokemonSpriteCache cache = new PokemonSpriteCache(temporaryDirectory);
        Files.write(cache.cachedFile(722), ONE_PIXEL_PNG);

        var image = cache.load(722).get(2, TimeUnit.SECONDS);

        assertTrue(image.isPresent());
        assertEquals(1, image.orElseThrow().getWidth());
        assertEquals(1, image.orElseThrow().getHeight());
    }

    @Test
    void ignoresInvalidDexNumbers() throws Exception {
        PokemonSpriteCache cache = new PokemonSpriteCache(temporaryDirectory);

        assertTrue(cache.load(0).get(1, TimeUnit.SECONDS).isEmpty());
    }
}
