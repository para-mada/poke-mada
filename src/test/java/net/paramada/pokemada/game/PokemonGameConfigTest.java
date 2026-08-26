package net.paramada.pokemada.game;

import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class PokemonGameConfigTest {
    @Test
    void pokemonMoonHasItsIdentityRamAndSaveConfiguration() {
        PokemonGameConfig config = PokemonGameConfig.pokemonMoon();

        assertEquals("0004000000175E00", config.titleId());
        assertEquals("Pokémon Moon", config.name());
        assertSame(SmMemoryMap.INSTANCE, config.ram().memoryMap());
        assertEquals("main", config.save().fileName());
        assertEquals("upload_save/", config.save().uploadEndpoint());
        assertEquals("00000001", config.save().directory().getFileName().toString());
    }

    @Test
    void fallsBackToWindowsRoamingDirectory() {
        assertEquals(Path.of("C:\\Users\\trainer", "AppData", "Roaming").toString(),
                PokemonGameConfig.environmentOrFallback(Map.of(), "APPDATA", "C:\\Users\\trainer"));
    }
}
