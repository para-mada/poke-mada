package net.paramada.pokemada.game.save;

import net.paramada.pokemada.game.assets.PokemonSpeciesDex;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads the six party slots directly from a Pokemon Sun/Moon main save. */
public final class SmSavePartyReader {
    private static final int PARTY_OFFSET = 0x1400;
    private static final int PARTY_SIZE = 0x104;

    public List<PartyPokemon> read(Path path) throws IOException {
        byte[] save = Files.readAllBytes(path);
        if (save.length != SmSaveEditor.SAVE_SIZE) {
            throw new IllegalArgumentException("El archivo no es un save de Sol/Luna");
        }
        List<PartyPokemon> result = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            int offset = PARTY_OFFSET + slot * PARTY_SIZE;
            byte[] encrypted = Arrays.copyOfRange(save, offset, offset + PARTY_SIZE);
            if (allZero(encrypted)) continue;
            byte[] pokemon = PokemonCrypto.decrypt(encrypted);
            int species = u16(pokemon, 0x08);
            if (species == 0) continue;
            if (!PokemonCrypto.hasValidStoredDataChecksum(pokemon)) {
                throw new IllegalStateException("Checksum PK7 inválido en el slot " + (slot + 1));
            }
            String nickname = decodeString(pokemon, 0x40, 24);
            result.add(new PartyPokemon(slot, species,
                    nickname.isBlank() ? PokemonSpeciesDex.nameOrFallback(species) : nickname,
                    Byte.toUnsignedInt(pokemon[0xec]), u16(pokemon, 0xf0), u16(pokemon, 0xf2)));
        }
        return List.copyOf(result);
    }

    private static boolean allZero(byte[] value) {
        for (byte current : value) if (current != 0) return false;
        return true;
    }

    private static String decodeString(byte[] data, int offset, int byteLength) {
        StringBuilder result = new StringBuilder();
        for (int index = offset; index + 1 < offset + byteLength; index += 2) {
            int value = u16(data, index);
            if (value == 0 || value == 0xffff) break;
            result.append((char) value);
        }
        return result.toString().trim();
    }

    private static int u16(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    public record PartyPokemon(int slot, int species, String name, int level, int currentHp, int maxHp) { }
}
