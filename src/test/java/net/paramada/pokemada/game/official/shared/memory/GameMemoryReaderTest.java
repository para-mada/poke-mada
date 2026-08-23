package net.paramada.pokemada.game.official.shared.memory;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.xy.XyMemoryMap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameMemoryReaderTest {
    @Test
    void resolvesCanonicalXyAddresses() {
        GameMemoryReader reader = new GameMemoryReader(new RecordingMemoryClient(), XyMemoryMap.INSTANCE);

        assertEquals(0x08ce1ce8L + 484L * 5, reader.partySlotAddress(5));
        assertEquals(0x082059e0L + 580L * 12,
                reader.battleSlotAddress(BattleEnvironment.TRAINER, 12));
        assertEquals(0x08c6a69cL - 9952,
                reader.bagPocketAddress(GameMemoryMap.BagPocket.BERRIES));
    }

    @Test
    void readsPartyDataFromThreeNormalizedRegions() throws IOException {
        RecordingMemoryClient memory = new RecordingMemoryClient();
        GameMemoryReader reader = new GameMemoryReader(memory, XyMemoryMap.INSTANCE);

        reader.readPartySlot(2);

        long slotAddress = 0x08ce1ce8L + 484L * 2;
        assertEquals(List.of(
                new ReadCall(slotAddress, 232),
                new ReadCall(slotAddress + 344, 22),
                new ReadCall(0x0820430cL + 580L * 2, 56)
        ), memory.reads);
    }

    @Test
    void rejectsSlotsOutsideKnownOfficialGameStructures() {
        GameMemoryReader reader = new GameMemoryReader(new RecordingMemoryClient(), XyMemoryMap.INSTANCE);

        assertThrows(IllegalArgumentException.class, () -> reader.partySlotAddress(6));
        assertThrows(IllegalArgumentException.class,
                () -> reader.battleSlotAddress(BattleEnvironment.WILD, 24));
    }

    @Test
    void decodesUtf16MessageAtAlignedNullTerminator() {
        byte[] message = "¡Hola!\0basura".getBytes(StandardCharsets.UTF_16LE);

        assertEquals("¡Hola!", MemoryTextDecoder.decodeUtf16Le(message));
    }

    private static final class RecordingMemoryClient implements MemoryClient {
        private final List<ReadCall> reads = new ArrayList<>();

        @Override
        public byte[] readMemory(long address, int size) {
            reads.add(new ReadCall(address, size));
            return new byte[size];
        }

        @Override
        public void writeMemory(long address, byte[] data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean testConnection() {
            return true;
        }

        @Override
        public void close() {
        }
    }

    private record ReadCall(long address, int size) {
    }
}
