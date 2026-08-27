package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Verified live editor for encrypted Pokemon Sun/Moon party data. */
public final class SmPartyEditor {
    private static final int SPECIES = 0x08;
    private static final int CHECKSUM = 0x06;
    private static final int NATURE = 0x1C;
    private static final int[] EV_OFFSETS = {0x1E, 0x1F, 0x20, 0x22, 0x23, 0x21};

    public Result modifyNature(MemoryClient memory, int slot, int expectedSpecies, int nature)
            throws IOException {
        if (nature < 0 || nature > 24) throw new IllegalArgumentException("Naturaleza inválida");
        return edit(memory, slot, expectedSpecies, pokemon -> {
            int before = Byte.toUnsignedInt(pokemon[NATURE]);
            pokemon[NATURE] = (byte) nature;
            return new Change("naturaleza", before, nature, NATURE);
        });
    }

    public Result addEv(MemoryClient memory, int slot, int expectedSpecies, String stat, int amount)
            throws IOException {
        if (amount < 1) throw new IllegalArgumentException("Cantidad de EV inválida");
        int statIndex = switch (stat) {
            case "hp" -> 0;
            case "attack" -> 1;
            case "defense" -> 2;
            case "special_attack" -> 3;
            case "special_defense" -> 4;
            case "speed" -> 5;
            default -> throw new IllegalArgumentException("Stat de EV inválido: " + stat);
        };
        int offset = EV_OFFSETS[statIndex];
        return edit(memory, slot, expectedSpecies, pokemon -> {
            int total = 0;
            for (int evOffset : EV_OFFSETS) total += Byte.toUnsignedInt(pokemon[evOffset]);
            int before = Byte.toUnsignedInt(pokemon[offset]);
            int applied = Math.min(amount, Math.min(252 - before, 510 - total));
            if (applied <= 0) throw new IllegalStateException("El Pokémon ya alcanzó el límite de EV");
            pokemon[offset] = (byte) (before + applied);
            return new Change(stat, before, before + applied, offset);
        });
    }

    private Result edit(MemoryClient memory, int slot, int expectedSpecies, Editor editor)
            throws IOException {
        if (slot < 0 || slot >= 6) throw new IllegalArgumentException("Slot de equipo inválido");
        var layout = SmMemoryMap.INSTANCE.party();
        long address = layout.address() + (long) layout.slotStride() * slot;
        byte[] stored = memory.readMemory(address, layout.pokemonDataSize());
        byte[] stats = memory.readMemory(address + layout.statsOffset(), layout.statsDataSize());
        byte[] decrypted = PokemonCrypto.decrypt(joined(stored, stats));
        if (!PokemonCrypto.hasValidStoredDataChecksum(decrypted)) {
            throw new IllegalStateException("Checksum PK7 inválido; no se escribió RAM");
        }
        if (u16(decrypted, SPECIES) != expectedSpecies) {
            throw new IllegalStateException("El equipo en RAM no coincide con el servidor");
        }

        Change change = editor.apply(decrypted);
        putU16(decrypted, CHECKSUM, PokemonCrypto.calculateStoredDataChecksum(decrypted));
        byte[] encrypted = PokemonCrypto.encrypt(decrypted);
        byte[] updatedStored = Arrays.copyOf(encrypted, stored.length);
        writeAndVerify(memory, address, updatedStored);

        byte[] verified = PokemonCrypto.decrypt(joined(
                memory.readMemory(address, stored.length),
                memory.readMemory(address + layout.statsOffset(), stats.length)));
        if (!PokemonCrypto.hasValidStoredDataChecksum(verified)
                || u16(verified, SPECIES) != expectedSpecies
                || Byte.toUnsignedInt(verified[change.offset]) != change.after) {
            throw new IOException("La verificación posterior del PK7 en RAM falló");
        }
        return new Result(change.label, change.before, change.after);
    }

    private static byte[] joined(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void writeAndVerify(MemoryClient memory, long address, byte[] expected)
            throws IOException {
        memory.writeMemory(address, expected);
        if (!Arrays.equals(expected, memory.readMemory(address, expected.length))) {
            throw new IOException("LimoMada3DS descartó la escritura en 0x%08X".formatted(address));
        }
    }

    private static int u16(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static void putU16(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
    }

    private interface Editor {
        Change apply(byte[] pokemon);
    }

    private record Change(String label, int before, int after, int offset) { }

    public record Result(String field, int before, int after) { }
}
