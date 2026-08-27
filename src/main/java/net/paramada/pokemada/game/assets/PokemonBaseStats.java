package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Local base-stat lookup. Never exposes calculated stats read from the running game. */
public final class PokemonBaseStats {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/pokemon-base-stats.csv";
    private static final String PAST_RESOURCE =
            "/net/paramada/pokemada/assets/pokemon-base-stats-past.csv";
    private static final int TARGET_GENERATION = 7;
    private static volatile Map<Integer, int[]> statsBySpecies = load();
    private static volatile Map<String, int[]> statsByForm = Map.of();

    private PokemonBaseStats() {
    }

    /** Returns Attack, Defense, Special Attack, Special Defense and Speed. */
    public static int[] forSpecies(int species) {
        int[] stats = statsBySpecies.get(species);
        return stats == null ? new int[5] : java.util.Arrays.copyOfRange(stats, 1, 6);
    }

    /** Returns HP, Attack, Defense, Special Attack, Special Defense and Speed. */
    public static int[] allForSpecies(int species) {
        int[] stats = statsBySpecies.get(species);
        return stats == null ? new int[6] : stats.clone();
    }

    public static int[] allForSpecies(int species, String form) {
        int[] stats = statsByForm.get(species + "/" + normalizeForm(form));
        return stats == null ? allForSpecies(species) : stats.clone();
    }

    static void install(Map<Integer, int[]> values) {
        Map<Integer, int[]> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, value.clone()));
        statsBySpecies = Map.copyOf(copy);
    }

    static void installForms(Map<String, int[]> values) {
        Map<String, int[]> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, value.clone()));
        statsByForm = Map.copyOf(copy);
    }

    private static String normalizeForm(String form) {
        return form == null || form.isBlank() ? "0" : form.toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<Integer, int[]> load() {
        Map<Integer, int[]> result = new HashMap<>();
        try (var input = PokemonBaseStats.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] columns = line.split(",", 4);
                    int species = Integer.parseInt(columns[0]);
                    int statId = Integer.parseInt(columns[1]);
                    if (statId >= 1 && statId <= 6) {
                        result.computeIfAbsent(species, ignored -> new int[6])[statId - 1] =
                                Integer.parseInt(columns[2]);
                    }
                }
            }
            applyHistoricalStats(result);
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Map.copyOf(result);
    }

    private static void applyHistoricalStats(Map<Integer, int[]> statsBySpecies) throws IOException {
        Map<Long, Integer> closestGeneration = new HashMap<>();
        try (var input = PokemonBaseStats.class.getResourceAsStream(PAST_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + PAST_RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] columns = line.split(",", 5);
                    int species = Integer.parseInt(columns[0]);
                    int generation = Integer.parseInt(columns[1]);
                    int statId = Integer.parseInt(columns[2]);
                    if (generation < TARGET_GENERATION || statId < 1 || statId > 6) continue;
                    long key = ((long) species << 3) | statId;
                    if (generation < closestGeneration.getOrDefault(key, Integer.MAX_VALUE)) {
                        statsBySpecies.computeIfAbsent(species, ignored -> new int[6])[statId - 1] =
                                Integer.parseInt(columns[3]);
                        closestGeneration.put(key, generation);
                    }
                }
            }
        }
    }
}
