package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

public final class MoveEffectiveness {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/type-efficacy.csv";
    private static final Set<Integer> FIXED_DAMAGE_MOVES = Set.of(49, 69, 82, 101);
    private static final double[][] CHART = loadChart();
    private static final Map<String, Integer> TYPE_IDS = Map.ofEntries(
            Map.entry("Normal", 1), Map.entry("Lucha", 2), Map.entry("Volador", 3),
            Map.entry("Veneno", 4), Map.entry("Tierra", 5), Map.entry("Roca", 6),
            Map.entry("Bicho", 7), Map.entry("Fantasma", 8), Map.entry("Acero", 9),
            Map.entry("Fuego", 10), Map.entry("Agua", 11), Map.entry("Planta", 12),
            Map.entry("Eléctrico", 13), Map.entry("Psíquico", 14), Map.entry("Hielo", 15),
            Map.entry("Dragón", 16), Map.entry("Siniestro", 17), Map.entry("Hada", 18));

    private MoveEffectiveness() {
    }

    public static OptionalDouble against(PokemonMoveDex.MoveInfo move, int enemyTypeOne, int enemyTypeTwo) {
        if (move.category().equalsIgnoreCase("estado") || FIXED_DAMAGE_MOVES.contains(move.id())) {
            return OptionalDouble.empty();
        }
        Integer attackingType = TYPE_IDS.get(move.type());
        if (attackingType == null || enemyTypeOne < 0 || enemyTypeOne > 17) return OptionalDouble.empty();
        int defendingOne = enemyTypeOne + 1;
        double multiplier = CHART[attackingType][defendingOne];
        if (enemyTypeTwo >= 0 && enemyTypeTwo <= 17 && enemyTypeTwo != enemyTypeOne) {
            multiplier *= CHART[attackingType][enemyTypeTwo + 1];
        }
        return OptionalDouble.of(multiplier);
    }

    /** Damage received from every attacking type, indexed by the game's 0-based type ID. */
    public static double[] defensiveMultipliers(int typeOne, int typeTwo) {
        double[] result = new double[18];
        if (typeOne < 0 || typeOne > 17) return result;
        for (int attack = 0; attack < result.length; attack++) {
            result[attack] = CHART[attack + 1][typeOne + 1];
            if (typeTwo >= 0 && typeTwo <= 17 && typeTwo != typeOne) {
                result[attack] *= CHART[attack + 1][typeTwo + 1];
            }
        }
        return result;
    }

    private static double[][] loadChart() {
        double[][] chart = new double[19][19];
        for (int attack = 1; attack <= 18; attack++) {
            java.util.Arrays.fill(chart[attack], 1.0);
        }
        try (var input = MoveEffectiveness.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] value = line.split(",");
                    int attack = Integer.parseInt(value[0]);
                    int defend = Integer.parseInt(value[1]);
                    if (attack <= 18 && defend <= 18) {
                        chart[attack][defend] = Integer.parseInt(value[2]) / 100.0;
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return chart;
    }
}
