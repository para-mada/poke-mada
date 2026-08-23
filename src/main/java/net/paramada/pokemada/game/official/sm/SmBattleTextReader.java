package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.memory.MemoryTextDecoder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reads Sun/Moon battle text, preferring the primary history buffer over the secondary fallback. */
public final class SmBattleTextReader {
    private final MemoryClient memory;

    public SmBattleTextReader(MemoryClient memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public BattleTextSnapshot read() throws IOException {
        Map<Long, String> mirrors = new LinkedHashMap<>();
        for (long address : SmMemoryMap.BATTLE_TEXT_RUNTIME_ADDRESSES) {
            String value = MemoryTextDecoder.decodeUtf16Le(
                    memory.readMemory(address, SmMemoryMap.BATTLE_TEXT_MIRROR_LENGTH));
            mirrors.put(address, value);
        }
        String primary = mirrors.getOrDefault(SmMemoryMap.BATTLE_TEXT_PRIMARY_ADDRESS, "");
        String secondary = mirrors.getOrDefault(SmMemoryMap.BATTLE_TEXT_SECONDARY_ADDRESS, "");
        String selected = isUsable(primary) ? primary : isUsable(secondary) ? secondary : "";
        return new BattleTextSnapshot(selected, mirrors);
    }

    private static boolean isUsable(String value) {
        if (value == null || value.isBlank()) return false;
        int latinLetters = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)
                    && Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) {
                latinLetters++;
                if (latinLetters >= 2) return true;
            }
        }
        return false;
    }

    public record BattleTextSnapshot(String message, Map<Long, String> mirrors) {
        public BattleTextSnapshot {
            message = Objects.requireNonNull(message, "message");
            mirrors = Map.copyOf(Objects.requireNonNull(mirrors, "mirrors"));
        }
    }
}
