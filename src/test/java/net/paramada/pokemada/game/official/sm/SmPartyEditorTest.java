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

final class SmPartyEditorTest {
    @Test
    void editsNatureAndEvsInEncryptedPartyRam() throws Exception {
        FakeMemory memory = memoryWithPokemon(25);
        SmPartyEditor editor = new SmPartyEditor();

        SmPartyEditor.Result nature = editor.modifyNature(memory, 0, 25, 13);
        assertEquals(0, nature.before());
        assertEquals(13, nature.after());
        assertEquals(13, decrypted(memory)[0x1c] & 0xff);

        SmPartyEditor.Result ev = editor.addEv(memory, 0, 25, "attack", 32);
        assertEquals(0, ev.before());
        assertEquals(32, ev.after());
        assertEquals(32, decrypted(memory)[0x1f] & 0xff);
        assertTrue(PokemonCrypto.hasValidStoredDataChecksum(decrypted(memory)));
        assertEquals(2, memory.writeCount);
    }

    @Test
    void capsEvsAndRefusesAStaleServerTarget() throws Exception {
        FakeMemory memory = memoryWithPokemon(25);
        SmPartyEditor editor = new SmPartyEditor();

        assertThrows(IllegalStateException.class,
                () -> editor.modifyNature(memory, 0, 26, 3));
        assertEquals(0, memory.writeCount);

        editor.addEv(memory, 0, 25, "attack", 999);
        assertEquals(252, decrypted(memory)[0x1f] & 0xff);
    }

    private static FakeMemory memoryWithPokemon(int species) {
        var layout = SmMemoryMap.INSTANCE.party();
        byte[] pokemon = new byte[layout.pokemonDataSize() + layout.statsDataSize()];
        putU32(pokemon, 0, 0x1234_5678);
        putU16(pokemon, 0x08, species);
        putU16(pokemon, 0x06, PokemonCrypto.calculateStoredDataChecksum(pokemon));
        byte[] encrypted = PokemonCrypto.encrypt(pokemon);
        FakeMemory memory = new FakeMemory();
        memory.put(layout.address(), Arrays.copyOf(encrypted, layout.pokemonDataSize()));
        memory.put(layout.address() + layout.statsOffset(), Arrays.copyOfRange(
                encrypted, layout.pokemonDataSize(), encrypted.length));
        return memory;
    }

    private static byte[] decrypted(FakeMemory memory) throws IOException {
        var layout = SmMemoryMap.INSTANCE.party();
        byte[] stored = memory.readMemory(layout.address(), layout.pokemonDataSize());
        byte[] stats = memory.readMemory(layout.address() + layout.statsOffset(), layout.statsDataSize());
        byte[] joined = Arrays.copyOf(stored, stored.length + stats.length);
        System.arraycopy(stats, 0, joined, stored.length, stats.length);
        return PokemonCrypto.decrypt(joined);
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

        void put(long address, byte[] value) {
            values.put(address, value.clone());
        }

        @Override
        public byte[] readMemory(long address, int size) throws IOException {
            byte[] value = values.get(address);
            if (value == null || value.length != size) throw new IOException("unexpected read");
            return value.clone();
        }

        @Override
        public void writeMemory(long address, byte[] data) {
            values.put(address, data.clone());
            writeCount++;
        }

        @Override public boolean testConnection() { return true; }
        @Override public void close() { }
    }
}
