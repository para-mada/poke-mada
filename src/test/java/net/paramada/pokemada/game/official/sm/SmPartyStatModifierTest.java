package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SmPartyStatModifierTest {
    @Test
    void modifiesEncryptedPartyAttackAndVerifiesTheWrite() throws Exception {
        var layout = SmMemoryMap.INSTANCE.party();
        byte[] decrypted = new byte[layout.pokemonDataSize() + layout.statsDataSize()];
        putU32(decrypted, 0, 0x1234_5678);
        putU16(decrypted, 0x08, 25);
        putU16(decrypted, 0xf4, 101);
        putU16(decrypted, 0x06, PokemonCrypto.calculateStoredDataChecksum(decrypted));
        byte[] encrypted = PokemonCrypto.encrypt(decrypted);
        FakeMemory memory = new FakeMemory();
        memory.put(layout.address(), java.util.Arrays.copyOf(encrypted, layout.pokemonDataSize()));
        memory.put(layout.address() + layout.statsOffset(), java.util.Arrays.copyOfRange(
                encrypted, layout.pokemonDataSize(), encrypted.length));

        SmPartyStatModifier.Result result = new SmPartyStatModifier().modify(memory, 0, "attack", 1);

        assertEquals(101, result.before());
        assertEquals(102, result.after());
        byte[] verified = PokemonCrypto.decrypt(joined(
                memory.readMemory(layout.address(), layout.pokemonDataSize()),
                memory.readMemory(layout.address() + layout.statsOffset(), layout.statsDataSize())));
        assertEquals(102, u16(verified, 0xf4));
        assertEquals(1, memory.writeCount);
    }

    private static byte[] joined(byte[] first, byte[] second) {
        byte[] result = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static int u16(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static void putU16(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
    }

    private static void putU32(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }

    private static final class FakeMemory implements MemoryClient {
        private final Map<Long, byte[]> values = new HashMap<>();
        private int writeCount;

        void put(long address, byte[] value) { values.put(address, value.clone()); }

        @Override public byte[] readMemory(long address, int size) throws IOException {
            byte[] value = values.get(address);
            if (value == null || value.length != size) throw new IOException("unexpected read");
            return value.clone();
        }

        @Override public void writeMemory(long address, byte[] data) {
            values.put(address, data.clone());
            writeCount++;
        }

        @Override public boolean testConnection() { return true; }
        @Override public void close() { }
    }
}
