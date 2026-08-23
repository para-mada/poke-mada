package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmPartyHealerTest {
    @Test
    void restoresHpStatusAndPpWithValidChecksum() throws Exception {
        FakeMemory memory = new FakeMemory();
        byte[] pokemon = pokemon(722, 7, 23, 33, 5, 2);
        memory.putPartySlot(0, pokemon);
        for (int slot = 1; slot < 6; slot++) memory.putPartySlot(slot, pokemon(0, 0, 0, 0, 0, 0));

        SmPartyHealer.HealResult result = new SmPartyHealer().heal(memory);
        byte[] healed = memory.decryptedSlot(0);

        assertEquals(1, result.occupiedSlots());
        assertEquals(1, result.healedSlots());
        assertEquals(23, u16(healed, 0xf0));
        assertEquals(0, u32(healed, 0xe8));
        assertEquals(49, Byte.toUnsignedInt(healed[0x62]));
        assertTrue(PokemonCrypto.hasValidStoredDataChecksum(healed));
        assertEquals(2, memory.writeCount);
    }

    @Test
    void doesNotWriteWhenPartyIsAlreadyRestored() throws Exception {
        FakeMemory memory = new FakeMemory();
        memory.putPartySlot(0, pokemon(722, 23, 23, 33, 49, 2));
        for (int slot = 1; slot < 6; slot++) memory.putPartySlot(slot, pokemon(0, 0, 0, 0, 0, 0));

        SmPartyHealer.HealResult result = new SmPartyHealer().heal(memory);

        assertEquals(0, result.healedSlots());
        assertEquals(0, memory.writeCount);
    }

    @Test
    void reportsWhenTheTransportSilentlyDiscardsWrites() {
        FakeMemory memory = new FakeMemory();
        memory.discardWrites = true;
        memory.putPartySlot(0, pokemon(722, 7, 23, 33, 5, 2));
        for (int slot = 1; slot < 6; slot++) memory.putPartySlot(slot, pokemon(0, 0, 0, 0, 0, 0));

        IOException error = assertThrows(IOException.class, () -> new SmPartyHealer().heal(memory));

        assertTrue(error.getMessage().contains("descartó la escritura"));
    }

    private static byte[] pokemon(int species, int hp, int maxHp, int move, int pp, int ppUps) {
        byte[] decrypted = new byte[254];
        ByteBuffer data = ByteBuffer.wrap(decrypted).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0, 0x1234_5678);
        data.putShort(0x08, (short) species);
        data.putShort(0x5a, (short) move);
        decrypted[0x62] = (byte) pp;
        decrypted[0x66] = (byte) ppUps;
        data.putInt(0xe8, species != 0 && hp != maxHp ? 1 : 0);
        data.putShort(0xf0, (short) hp);
        data.putShort(0xf2, (short) maxHp);
        data.putShort(0x06, (short) PokemonCrypto.calculateStoredDataChecksum(decrypted));
        return decrypted;
    }

    private static int u16(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static long u32(byte[] data, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private static final class FakeMemory implements MemoryClient {
        private final Map<Long, byte[]> regions = new HashMap<>();
        private int writeCount;
        private boolean discardWrites;

        private void putPartySlot(int slot, byte[] decrypted) {
            var layout = SmMemoryMap.INSTANCE.party();
            long address = layout.address() + (long) layout.slotStride() * slot;
            byte[] encrypted = PokemonCrypto.encrypt(decrypted);
            regions.put(address, Arrays.copyOf(encrypted, layout.pokemonDataSize()));
            regions.put(address + layout.statsOffset(), Arrays.copyOfRange(encrypted,
                    layout.pokemonDataSize(), layout.pokemonDataSize() + layout.statsDataSize()));
        }

        private byte[] decryptedSlot(int slot) {
            var layout = SmMemoryMap.INSTANCE.party();
            long address = layout.address() + (long) layout.slotStride() * slot;
            byte[] combined = new byte[layout.pokemonDataSize() + layout.statsDataSize()];
            System.arraycopy(regions.get(address), 0, combined, 0, layout.pokemonDataSize());
            System.arraycopy(regions.get(address + layout.statsOffset()), 0, combined,
                    layout.pokemonDataSize(), layout.statsDataSize());
            return PokemonCrypto.decrypt(combined);
        }

        @Override public byte[] readMemory(long address, int size) throws IOException {
            byte[] data = regions.get(address);
            if (data == null || data.length != size) throw new IOException("Unexpected read");
            return data.clone();
        }

        @Override public void writeMemory(long address, byte[] data) {
            writeCount++;
            if (!discardWrites) regions.put(address, data.clone());
        }

        @Override public boolean testConnection() { return true; }
        @Override public void close() {}
    }
}
