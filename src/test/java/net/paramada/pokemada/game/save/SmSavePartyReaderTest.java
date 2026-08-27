package net.paramada.pokemada.game.save;

import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SmSavePartyReaderTest {
    @TempDir Path temporary;

    @Test
    void readsOccupiedPartySlotsFromTheLocalSave() throws Exception {
        Path main = temporary.resolve("main");
        byte[] save = new byte[SmSaveEditor.SAVE_SIZE];
        putPokemon(save, 2, 25, "Chispa", 37, 88, 101);
        Files.write(main, save);

        var party = new SmSavePartyReader().read(main);

        assertEquals(1, party.size());
        assertEquals(2, party.getFirst().slot());
        assertEquals(25, party.getFirst().species());
        assertEquals("Chispa", party.getFirst().name());
        assertEquals(37, party.getFirst().level());
        assertEquals(88, party.getFirst().currentHp());
        assertEquals(101, party.getFirst().maxHp());
    }

    @Test
    void rejectsAnInvalidSaveSize() throws Exception {
        Path main = temporary.resolve("main");
        Files.write(main, new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> new SmSavePartyReader().read(main));
    }

    private static void putPokemon(byte[] save, int slot, int species, String nickname,
                                   int level, int currentHp, int maxHp) {
        byte[] pokemon = new byte[PokemonCrypto.PARTY_SIZE];
        putU32(pokemon, 0, 0x1234_5678 + slot);
        putU16(pokemon, 0x08, species);
        for (int index = 0; index < nickname.length(); index++) {
            putU16(pokemon, 0x40 + index * 2, nickname.charAt(index));
        }
        pokemon[0xec] = (byte) level;
        putU16(pokemon, 0xf0, currentHp);
        putU16(pokemon, 0xf2, maxHp);
        putU16(pokemon, 0x06, PokemonCrypto.calculateStoredDataChecksum(pokemon));
        byte[] encrypted = PokemonCrypto.encrypt(pokemon);
        System.arraycopy(encrypted, 0, save, 0x1400 + slot * 0x104, encrypted.length);
    }

    private static void putU16(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
    }

    private static void putU32(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }
}
