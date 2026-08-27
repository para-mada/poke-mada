package net.paramada.pokemada.game.assets;

import net.paramada.pokemada.server.Json;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Installs one server-produced catalog into every local lookup atomically per lookup class. */
public final class GameDataCatalog {
    private static final Map<String, Integer> TYPE_IDS = Map.ofEntries(
            Map.entry("normal", 0), Map.entry("fighting", 1), Map.entry("flying", 2),
            Map.entry("poison", 3), Map.entry("ground", 4), Map.entry("rock", 5),
            Map.entry("bug", 6), Map.entry("ghost", 7), Map.entry("steel", 8),
            Map.entry("fire", 9), Map.entry("water", 10), Map.entry("grass", 11),
            Map.entry("electric", 12), Map.entry("psychic", 13), Map.entry("ice", 14),
            Map.entry("dragon", 15), Map.entry("dark", 16), Map.entry("fairy", 17));

    private GameDataCatalog() { }

    public static void install(String json) {
        Map<String, Object> root = object(Json.parse(json));
        Map<Integer, int[]> stats = new HashMap<>();
        Map<Integer, int[]> types = new HashMap<>();
        Map<String, int[]> formStats = new HashMap<>();
        Map<String, int[]> formTypes = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        for (Object value : array(root.get("pokemon"))) {
            Map<String, Object> row = object(value);
            int dex = integer(row.get("dex_number"));
            String form = String.valueOf(row.get("form"));
            int[] rowStats = array(row.get("base_stats")).stream().mapToInt(GameDataCatalog::integer).toArray();
            int[] ids = array(row.get("types")).stream().map(String::valueOf)
                    .map(valueName -> TYPE_IDS.get(valueName.toLowerCase(Locale.ROOT)))
                    .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).toArray();
            int[] rowTypes = ids.length == 1 ? new int[]{ids[0], ids[0]}
                    : ids.length >= 2 ? new int[]{ids[0], ids[1]} : new int[]{-1, -1};
            formStats.put(dex + "/" + form, rowStats);
            formTypes.put(dex + "/" + form, rowTypes);
            if ("0".equals(form)) {
                names.put(dex, String.valueOf(row.get("name")));
                stats.put(dex, rowStats);
                types.put(dex, rowTypes);
            }
        }
        Map<Integer, PokemonMoveDex.MoveInfo> moves = new HashMap<>();
        for (Object value : array(root.get("moves"))) {
            Map<String, Object> row = object(value);
            int id = integer(row.get("id"));
            moves.put(id, new PokemonMoveDex.MoveInfo(id, string(row.get("name")), string(row.get("type")),
                    string(row.get("category")), integer(row.get("power")), integer(row.get("pp")),
                    integer(row.get("accuracy")), string(row.get("description"))));
        }
        if (!stats.isEmpty()) PokemonBaseStats.install(stats);
        if (!formStats.isEmpty()) PokemonBaseStats.installForms(formStats);
        if (!types.isEmpty()) PokemonTypeDex.install(types);
        if (!formTypes.isEmpty()) PokemonTypeDex.installForms(formTypes);
        if (!names.isEmpty()) PokemonSpeciesDex.install(names);
        if (!moves.isEmpty()) PokemonMoveDex.install(moves);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("expected catalog object");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value) {
        if (value instanceof List<?> list) return (List<Object>) list;
        throw new IllegalArgumentException("expected catalog array");
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
