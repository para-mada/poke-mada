package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class PokemonAbilityDex {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/abilities-gen7.tsv";
    private static final Map<Integer, AbilityInfo> ABILITIES = load();

    private PokemonAbilityDex() {
    }

    public static Optional<AbilityInfo> find(int id) {
        return Optional.ofNullable(ABILITIES.get(id));
    }

    private static Map<Integer, AbilityInfo> load() {
        Map<Integer, AbilityInfo> result = new HashMap<>();
        try (var input = PokemonAbilityDex.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] value = line.split("\t", -1);
                    int id = Integer.parseInt(value[0]);
                    result.put(id, new AbilityInfo(id, decode(value[1]), decode(value[2])));
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

    public record AbilityInfo(int id, String name, String description) {
    }
}
