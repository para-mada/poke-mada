package net.paramada.pokemada.game;

import net.paramada.pokemada.game.official.shared.memory.GameMemoryMap;
import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import net.paramada.pokemada.protocol.citra.CitraUdpClient;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** General identity, RAM and persisted-save configuration for a supported game. */
public record PokemonGameConfig(
        String titleId,
        String name,
        RamConfig ram,
        SaveConfig save
) {
    public static final String POKEMON_MOON_TITLE_ID = "0004000000175E00";

    public PokemonGameConfig {
        titleId = requireText(titleId, "titleId").toUpperCase();
        name = requireText(name, "name");
        ram = Objects.requireNonNull(ram, "ram");
        save = Objects.requireNonNull(save, "save");
    }

    public static PokemonGameConfig pokemonMoon() {
        String roaming = environmentOrFallback(System.getenv(), "APPDATA", System.getProperty("user.home"));
        Path saveDirectory = Path.of(roaming, "Lime3DS", "sdmc", "Nintendo 3DS",
                "00000000000000000000000000000000", "00000000000000000000000000000000",
                "title", "00040000", "00175e00", "data", "00000001");
        return new PokemonGameConfig(POKEMON_MOON_TITLE_ID, "Pokémon Moon",
                new RamConfig("localhost", CitraUdpClient.DEFAULT_PORT, SmMemoryMap.INSTANCE),
                new SaveConfig(saveDirectory, "main", "upload_save/"));
    }

    static String environmentOrFallback(Map<String, String> environment, String key, String fallbackHome) {
        String value = environment.get(key);
        if (value != null && !value.isBlank()) return value;
        return Path.of(Objects.requireNonNull(fallbackHome, "fallbackHome"), "AppData", "Roaming").toString();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public record RamConfig(String host, int port, GameMemoryMap memoryMap) {
        public RamConfig {
            host = requireText(host, "ram.host");
            if (port < 1 || port > 65_535) throw new IllegalArgumentException("ram.port is invalid");
            memoryMap = Objects.requireNonNull(memoryMap, "ram.memoryMap");
        }
    }

    public record SaveConfig(Path directory, String fileName, String uploadEndpoint) {
        public SaveConfig {
            directory = Objects.requireNonNull(directory, "save.directory").toAbsolutePath().normalize();
            fileName = requireText(fileName, "save.fileName");
            uploadEndpoint = requireText(uploadEndpoint, "save.uploadEndpoint");
        }

        public Path file() {
            return directory.resolve(fileName);
        }
    }
}
