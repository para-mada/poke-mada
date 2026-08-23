package net.paramada.pokemada.game.official.sm.inventory;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

final class SmBagReaderTest {
    @Test
    void performsOneReadOfTheGuestVirtualMyItemBlockAndNeverWrites() throws Exception {
        RecordingMemory memory = new RecordingMemory();
        new SmBagReader(memory).read();

        assertEquals(SmMemoryMap.BAG_BLOCK_ADDRESS, memory.address);
        assertEquals(SmMemoryMap.BAG_BLOCK_LENGTH, memory.size);
    }

    private static final class RecordingMemory implements MemoryClient {
        long address;
        int size;

        @Override
        public byte[] readMemory(long address, int size) {
            this.address = address;
            this.size = size;
            return new byte[size];
        }

        @Override
        public void writeMemory(long address, byte[] data) {
            fail("bag reader must never write memory");
        }

        @Override public boolean testConnection() { return true; }
        @Override public void close() { }
    }
}
