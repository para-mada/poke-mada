package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Offline species-name catalog for the National Pokédex through generation 7. */
public final class PokemonSpeciesDex {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/pokemon-species-gen7.tsv";
    private static volatile Map<Integer, String> names = load(RESOURCE);
    private static final Map<Integer, String> BASE_STAGES = load(
            "/net/paramada/pokemada/assets/pokemon-base-stages.tsv");

    private PokemonSpeciesDex() {
    }

    public static Optional<String> find(int species) {
        return Optional.ofNullable(names.get(species));
    }

    static void install(Map<Integer, String> values) {
        names = Map.copyOf(values);
    }

    public static String nameOrFallback(int species) {
        return find(species).orElse("Pokémon #" + species);
    }

    /** Includes babies and single-stage species; excludes every species with a pre-evolution. */
    public static boolean isBaseStage(int species) {
        return BASE_STAGES.containsKey(species);
    }

    private static Map<Integer, String> load(String resource) {
        Map<Integer, String> names = new HashMap<>();
        try (var stream = PokemonSpeciesDex.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing species catalog: " + resource);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t", 2);
                    if (fields.length != 2) throw new IllegalStateException("Invalid species row: " + line);
                    int id = Integer.parseInt(fields[0]);
                    if (names.put(id, fields[1]) != null) {
                        throw new IllegalStateException("Duplicate species id: " + id);
                    }
                }
            }
        } catch (IOException | NumberFormatException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Map.copyOf(names);
    }
}
