package net.paramada.pokemada.game.assets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Localized move metadata through Generation VII, loaded once from a bundled catalog. */
public final class PokemonMoveDex {
    private static final String RESOURCE = "/net/paramada/pokemada/assets/moves-gen7.tsv";
    private static volatile Map<Integer, MoveInfo> moves = load();

    private PokemonMoveDex() {
    }

    public static Optional<MoveInfo> find(int moveId) {
        return Optional.ofNullable(moves.get(moveId));
    }

    static void install(Map<Integer, MoveInfo> values) {
        moves = Map.copyOf(values);
    }

    private static Map<Integer, MoveInfo> load() {
        Map<Integer, MoveInfo> result = new HashMap<>();
        try (var input = PokemonMoveDex.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] value = line.split("\t", -1);
                    int id = Integer.parseInt(value[0]);
                    result.put(id, new MoveInfo(id, decode(value[1]), decode(value[2]), decode(value[3]),
                            Integer.parseInt(value[4]), Integer.parseInt(value[5]),
                            Integer.parseInt(value[6]), decode(value[7])));
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ExceptionInInitializerError(exception);
        }
        return Map.copyOf(result);
    }

    private static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    public record MoveInfo(int id, String name, String type, String category, int power,
                           int pp, int accuracy, String description) {
    }
}
