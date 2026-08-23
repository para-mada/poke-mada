package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.memory.MemoryTextDecoder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reads the two stable Sun/Moon battle-text mirrors and returns a value only when both agree. */
public final class SmBattleTextReader {
    private final MemoryClient memory;

    public SmBattleTextReader(MemoryClient memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public BattleTextSnapshot read() throws IOException {
        Map<Long, String> mirrors = new LinkedHashMap<>();
        for (long address : SmMemoryMap.BATTLE_TEXT_CONSENSUS_ADDRESSES) {
            String value = MemoryTextDecoder.decodeUtf16Le(
                    memory.readMemory(address, SmMemoryMap.BATTLE_TEXT_MIRROR_LENGTH));
            mirrors.put(address, value);
        }
        String selected = mirrors.values().stream()
                .filter(value -> !value.isBlank())
                .filter(value -> frequency(mirrors, value) >= 2)
                .findFirst()
                .orElse("");
        return new BattleTextSnapshot(selected, mirrors);
    }

    private static int frequency(Map<Long, String> mirrors, String value) {
        return (int) mirrors.values().stream().filter(value::equals).count();
    }

    public record BattleTextSnapshot(String message, Map<Long, String> mirrors) {
        public BattleTextSnapshot {
            message = Objects.requireNonNull(message, "message");
            mirrors = Map.copyOf(Objects.requireNonNull(mirrors, "mirrors"));
        }
    }
}
