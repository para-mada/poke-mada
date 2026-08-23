package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PokemonItemDex {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/items-gen7.tsv";
    private static final Map<Integer, ItemInfo> ITEMS = load();

    private PokemonItemDex() {
    }

    public static Optional<ItemInfo> find(int gameId) {
        return Optional.ofNullable(ITEMS.get(gameId));
    }

    private static Map<Integer, ItemInfo> load() {
        Map<Integer, ItemInfo> result = new HashMap<>();
        try (var input = PokemonItemDex.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] value = line.split("\t", -1);
                    int gameId = Integer.parseInt(value[0]);
                    result.put(gameId, new ItemInfo(gameId, value[1], decode(value[2]), decode(value[3])));
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Map.copyOf(result);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    public record ItemInfo(int gameId, String identifier, String name, String description) {
    }
}
