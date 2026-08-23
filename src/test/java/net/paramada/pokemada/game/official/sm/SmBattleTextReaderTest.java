package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmBattleTextReaderTest {
    @Test
    void acceptsPrimaryAndIgnoresRenderBoxBuffer() throws Exception {
        Map<Long, byte[]> contents = new HashMap<>();
        contents.put(SmMemoryMap.BATTLE_TEXT_PRIMARY_ADDRESS, text("¡Es supereficaz!"));
        contents.put(SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS, text("¡Es superefi"));
        contents.put(SmMemoryMap.BATTLE_TEXT_SECONDARY_ADDRESS, text("¡Es supereficaz!"));

        SmBattleTextReader.BattleTextSnapshot snapshot = new SmBattleTextReader(new FakeMemory(contents)).read();

        assertEquals("¡Es supereficaz!", snapshot.message());
        assertEquals(2, snapshot.mirrors().size());
        assertEquals(false, snapshot.mirrors().containsKey(SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS));
    }

    @Test
    void prioritizesPrimaryWhenSecondaryDisagrees() throws Exception {
        Map<Long, byte[]> contents = new HashMap<>();
        contents.put(SmMemoryMap.BATTLE_TEXT_PRIMARY_ADDRESS, text("mensaje uno"));
        contents.put(SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS, text("mensaje uno"));
        contents.put(SmMemoryMap.BATTLE_TEXT_SECONDARY_ADDRESS, text("mensaje dos"));

        SmBattleTextReader.BattleTextSnapshot snapshot = new SmBattleTextReader(new FakeMemory(contents)).read();

        assertEquals("mensaje uno", snapshot.message());
    }

    @Test
    void fallsBackToSecondaryWhenPrimaryDoesNotContainUsableText() throws Exception {
        Map<Long, byte[]> contents = new HashMap<>();
        contents.put(SmMemoryMap.BATTLE_TEXT_PRIMARY_ADDRESS, text("\u0010\u0002?"));
        contents.put(SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS, text("texto visible que no debe usarse"));
        contents.put(SmMemoryMap.BATTLE_TEXT_SECONDARY_ADDRESS, text("Pikachu ha usado Impactrueno."));

        SmBattleTextReader.BattleTextSnapshot snapshot = new SmBattleTextReader(new FakeMemory(contents)).read();

        assertEquals("Pikachu ha usado Impactrueno.", snapshot.message());
        assertEquals(false, snapshot.mirrors().containsKey(SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS));
    }

    private static byte[] text(String value) {
        byte[] result = new byte[SmMemoryMap.BATTLE_TEXT_MIRROR_LENGTH];
        byte[] encoded = (value + "\0").getBytes(StandardCharsets.UTF_16LE);
        System.arraycopy(encoded, 0, result, 0, encoded.length);
        return result;
    }

    private record FakeMemory(Map<Long, byte[]> contents) implements MemoryClient {
        @Override
        public byte[] readMemory(long address, int size) {
            return contents.getOrDefault(address, new byte[size]).clone();
        }

        @Override
        public void writeMemory(long address, byte[] data) {
            throw new AssertionError("battle text reader must remain read-only");
        }

        @Override
        public boolean testConnection() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
