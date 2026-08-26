package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

/** Verified Sun/Moon party-stat writer used by authorized virtual-item commands. */
public final class SmPartyStatModifier {
    private static final int SPECIES = 0x08;
    private static final Map<String, Integer> STAT_OFFSETS = Map.of(
            "attack", 0xf4,
            "defense", 0xf6,
            "speed", 0xf8,
            "special_attack", 0xfa,
            "special_defense", 0xfc);

    public Result modify(MemoryClient memory, int slot, String stat, int amount) throws IOException {
        if (slot < 0 || slot >= 6) throw new IllegalArgumentException("party slot must be between 0 and 5");
        Integer offset = STAT_OFFSETS.get(stat);
        if (offset == null) throw new IllegalArgumentException("unsupported stat: " + stat);
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");

        var layout = SmMemoryMap.INSTANCE.party();
        long address = layout.address() + (long) layout.slotStride() * slot;
        byte[] stored = memory.readMemory(address, layout.pokemonDataSize());
        byte[] stats = memory.readMemory(address + layout.statsOffset(), layout.statsDataSize());
        byte[] encrypted = joined(stored, stats);
        byte[] decrypted = PokemonCrypto.decrypt(encrypted);
        if (u16(decrypted, SPECIES) == 0) throw new IOException("El slot seleccionado está vacío");
        if (!PokemonCrypto.hasValidStoredDataChecksum(decrypted)) {
            throw new IOException("Checksum inválido; no se escribió RAM");
        }
        int before = u16(decrypted, offset);
        int after = Math.addExact(before, amount);
        if (after > 0xffff) throw new IOException("El stat excedería el máximo permitido");
        putU16(decrypted, offset, after);
        byte[] updated = PokemonCrypto.encrypt(decrypted);
        byte[] updatedStats = Arrays.copyOfRange(updated, stored.length, stored.length + stats.length);
        writeAndVerify(memory, address + layout.statsOffset(), updatedStats);

        byte[] verified = PokemonCrypto.decrypt(joined(
                memory.readMemory(address, stored.length),
                memory.readMemory(address + layout.statsOffset(), stats.length)));
        if (u16(verified, offset) != after) throw new IOException("La verificación del stat falló");
        return new Result(slot, stat, before, after);
    }

    private static byte[] joined(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void writeAndVerify(MemoryClient memory, long address, byte[] expected) throws IOException {
        memory.writeMemory(address, expected);
        if (!Arrays.equals(expected, memory.readMemory(address, expected.length))) {
            throw new IOException("MadaLime descartó la escritura en 0x%08X".formatted(address));
        }
    }

    private static int u16(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static void putU16(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
    }

    public record Result(int slot, String stat, int before, int after) { }
}
