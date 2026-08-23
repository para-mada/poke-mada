package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.assets.PokemonMoveDex;
import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Explicit, Sun/Moon-only party healing writer used by the Poke Vial feature. */
public final class SmPartyHealer {
    private static final int SPECIES = 0x08;
    private static final int CHECKSUM = 0x06;
    private static final int STATUS = 0xe8;
    private static final int CURRENT_HP = 0xf0;
    private static final int MAX_HP = 0xf2;
    private static final int MOVE_IDS = 0x5a;
    private static final int CURRENT_PP = 0x62;
    private static final int PP_UPS = 0x66;

    public HealResult heal(MemoryClient memory) throws IOException {
        var layout = SmMemoryMap.INSTANCE.party();
        List<PreparedSlot> changed = new ArrayList<>();
        int occupied = 0;
        for (int slot = 0; slot < 6; slot++) {
            long address = layout.address() + (long) layout.slotStride() * slot;
            byte[] stored = memory.readMemory(address, layout.pokemonDataSize());
            byte[] stats = memory.readMemory(address + layout.statsOffset(), layout.statsDataSize());
            byte[] encrypted = new byte[stored.length + stats.length];
            System.arraycopy(stored, 0, encrypted, 0, stored.length);
            System.arraycopy(stats, 0, encrypted, stored.length, stats.length);
            byte[] decrypted = PokemonCrypto.decrypt(encrypted);
            if (u16(decrypted, SPECIES) == 0) continue;
            occupied++;
            if (!PokemonCrypto.hasValidStoredDataChecksum(decrypted)) {
                throw new IOException("Checksum inválido en el slot " + (slot + 1) + "; no se escribió RAM");
            }
            boolean needsHealing = u16(decrypted, CURRENT_HP) != u16(decrypted, MAX_HP)
                    || u32(decrypted, STATUS) != 0;
            for (int move = 0; move < 4; move++) {
                int moveId = u16(decrypted, MOVE_IDS + move * 2);
                int maxPp = maxPp(moveId, Byte.toUnsignedInt(decrypted[PP_UPS + move]));
                if (maxPp >= 0) {
                    if (Byte.toUnsignedInt(decrypted[CURRENT_PP + move]) != maxPp) needsHealing = true;
                    decrypted[CURRENT_PP + move] = (byte) maxPp;
                }
            }
            if (!needsHealing) continue;
            putU32(decrypted, STATUS, 0);
            putU16(decrypted, CURRENT_HP, u16(decrypted, MAX_HP));
            putU16(decrypted, CHECKSUM, PokemonCrypto.calculateStoredDataChecksum(decrypted));
            changed.add(new PreparedSlot(address, PokemonCrypto.encrypt(decrypted), stored.length, stats.length,
                    layout.statsOffset()));
        }
        if (occupied == 0) throw new IOException("No hay Pokémon válidos en el equipo");
        for (PreparedSlot slot : changed) {
            memory.writeMemory(slot.address(), java.util.Arrays.copyOf(slot.encrypted(), slot.storedSize()));
            memory.writeMemory(slot.address() + slot.statsOffset(), java.util.Arrays.copyOfRange(
                    slot.encrypted(), slot.storedSize(), slot.storedSize() + slot.statsSize()));
        }
        return new HealResult(occupied, changed.size());
    }

    public static int maxPp(int moveId, int ppUps) {
        if (moveId == 0) return 0;
        int base = PokemonMoveDex.find(moveId).map(PokemonMoveDex.MoveInfo::pp).orElse(-1);
        if (base < 0) return -1;
        return base * (5 + Math.clamp(ppUps, 0, 3)) / 5;
    }

    private static int u16(byte[] data, int offset) {
        return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
    }

    private static long u32(byte[] data, int offset) {
        return Integer.toUnsignedLong(ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private static void putU16(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
    }

    private static void putU32(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }

    private record PreparedSlot(long address, byte[] encrypted, int storedSize, int statsSize, int statsOffset) {}
    public record HealResult(int occupiedSlots, int healedSlots) {}
}
