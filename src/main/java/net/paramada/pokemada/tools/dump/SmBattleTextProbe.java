package net.paramada.pokemada.tools.dump;

import net.paramada.pokemada.battlelog.BattleMessageSanitizer;
import net.paramada.pokemada.game.official.shared.memory.MemoryTextDecoder;
import net.paramada.pokemada.game.official.sm.SmBattleTextReader;
import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import net.paramada.pokemada.protocol.citra.CitraUdpClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;

/** Read-only command-line probe for the three Sun/Moon battle text mirrors. */
public final class SmBattleTextProbe {
    private SmBattleTextProbe() {
    }

    public static void main(String[] args) throws Exception {
        try (var client = new CitraUdpClient()) {
            var values = new ArrayList<String>();
            for (long address : SmMemoryMap.BATTLE_TEXT_MIRROR_ADDRESSES) {
                byte[] raw = client.readMemory(address, SmMemoryMap.BATTLE_TEXT_MIRROR_LENGTH);
                String decoded = MemoryTextDecoder.decodeUtf16Le(raw);
                values.add(decoded);
                System.out.printf("0x%08X [%s] | %s%n", address, role(address), printable(decoded));
                System.out.printf("  limpio: %s%n", printable(BattleMessageSanitizer.sanitize(decoded)));
                System.out.printf("  bytes:  %s%n", HexFormat.ofDelimiter(" ").formatHex(Arrays.copyOf(raw, 32)));
            }
            String consensus = new SmBattleTextReader(client).read().message();
            System.out.printf("CONSENSO: %s%n", consensus.isBlank() ? "<sin consenso 2-de-3>" : printable(consensus));
            System.out.printf("LIMPIO:   %s%n", printable(BattleMessageSanitizer.sanitize(consensus)));
            System.out.printf("COINCIDEN: %s%n", values.stream().distinct().count() == 1
                    ? "3/3" : "no (" + values.stream().distinct().count() + " valores)");
        }
    }

    private static String printable(String value) {
        return value.isBlank() ? "<vacío>" : value;
    }

    private static String role(long address) {
        return address == SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS
                ? "render-box, excluida" : "estable, consenso";
    }
}
