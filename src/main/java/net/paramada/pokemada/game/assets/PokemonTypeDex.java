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
    private static final Map<Integer, int[]> TYPES = load();

    private PokemonTypeDex() {
    }

    public static int[] forSpecies(int species) {
        int[] result = TYPES.get(species);
        return result == null ? new int[]{-1, -1} : result.clone();
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
