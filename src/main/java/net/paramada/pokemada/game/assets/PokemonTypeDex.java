package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Local Gen VII lookup for a species' defensive types (0-based game type IDs). */
public final class PokemonTypeDex {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/pokemon-types-gen7.csv";
    private static volatile Map<Integer, int[]> types = load();
    private static volatile Map<String, int[]> typesByForm = Map.of();

    private PokemonTypeDex() {
    }

    public static int[] forSpecies(int species) {
        int[] result = types.get(species);
        return result == null ? new int[]{-1, -1} : result.clone();
    }

    public static int[] forSpecies(int species, String form) {
        int[] result = typesByForm.get(species + "/" + normalizeForm(form));
        return result == null ? forSpecies(species) : result.clone();
    }

    static void install(Map<Integer, int[]> values) {
        Map<Integer, int[]> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, value.clone()));
        types = Map.copyOf(copy);
    }

    static void installForms(Map<String, int[]> values) {
        Map<String, int[]> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, value.clone()));
        typesByForm = Map.copyOf(copy);
    }

    private static String normalizeForm(String form) {
        return form == null || form.isBlank() ? "0" : form.toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<Integer, int[]> load() {
        Map<Integer, int[]> result = new HashMap<>();
        try (var input = PokemonTypeDex.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] value = line.split(",");
                    result.put(Integer.parseInt(value[0]),
                            new int[]{Integer.parseInt(value[1]), Integer.parseInt(value[2])});
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Map.copyOf(result);
    }
}
