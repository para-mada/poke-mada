package net.paramada.pokemada.game.official.shared.memory;

import net.paramada.pokemada.game.memory.MemoryClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public final class GameMemoryReader {
    private final MemoryClient memory;
    private final GameMemoryMap map;

    public GameMemoryReader(MemoryClient memory, GameMemoryMap map) {
        this.memory = Objects.requireNonNull(memory, "memory");
        this.map = Objects.requireNonNull(map, "map");
    }

    public PartySlotData readPartySlot(int slot) throws IOException {
        requireSlot(slot, 6, "party");
        GameMemoryMap.PartyLayout party = map.party();
        long slotAddress = offsetAddress(party.address(), party.slotStride(), slot);

        byte[] pokemon = memory.readMemory(slotAddress, party.pokemonDataSize());
        byte[] stats = memory.readMemory(slotAddress + party.statsOffset(), party.statsDataSize());
        byte[] moves = memory.readMemory(
                offsetAddress(party.moveDataAddress(), party.moveDataStride(), slot), party.moveDataSize());
        return new PartySlotData(pokemon, stats, moves);
    }

    public byte[] readBattlePokemon(BattleEnvironment environment, int memorySlot) throws IOException {
        requireSlot(memorySlot, 24, "battle");
        GameMemoryMap.CombatLayout combat = map.battle(environment).combat();
        long address = offsetAddress(combat.address(), combat.pokemonStride(), memorySlot);
        return memory.readMemory(address, combat.pokemonDataSize());
    }

    public byte[] readFrontalPokemon(int slot) throws IOException {
        requireSlot(slot, 6, "frontal");
        GameMemoryMap.CombatLayout combat = map.battle(BattleEnvironment.WILD).combat();
        long firstAddress = combat.selectedPokemonAddress() - combat.frontalPokemonStride();
        long address = offsetAddress(firstAddress, combat.frontalPokemonStride(), slot);
        return memory.readMemory(address, combat.pokemonDataSize());
    }

    public String readMessage(long address) throws IOException {
        return readMessage(address, map.messages().chatLength());
    }

    public String readMessage(long address, int length) throws IOException {
        if (length <= 0) {
            throw new IllegalArgumentException("message length must be positive");
        }
        return MemoryTextDecoder.decodeUtf16Le(memory.readMemory(address, length));
    }

    public long partySlotAddress(int slot) {
        requireSlot(slot, 6, "party");
        return offsetAddress(map.party().address(), map.party().slotStride(), slot);
    }

    public long battleSlotAddress(BattleEnvironment environment, int memorySlot) {
        requireSlot(memorySlot, 24, "battle");
        GameMemoryMap.CombatLayout combat = map.battle(environment).combat();
        return offsetAddress(combat.address(), combat.pokemonStride(), memorySlot);
    }

    public long bagPocketAddress(GameMemoryMap.BagPocket pocket) {
        GameMemoryMap.PocketLayout layout = map.items().pocket(pocket);
        return map.items().bagAnchorAddress() - layout.offset();
    }

    private static long offsetAddress(long base, int stride, int slot) {
        return Math.addExact(base, Math.multiplyExact((long) stride, slot));
    }

    private static void requireSlot(int slot, int count, String description) {
        if (slot < 0 || slot >= count) {
            throw new IllegalArgumentException(description + " slot must be between 0 and " + (count - 1));
        }
    }

    public record PartySlotData(byte[] pokemon, byte[] stats, byte[] moves) {
        public PartySlotData {
            pokemon = Objects.requireNonNull(pokemon, "pokemon").clone();
            stats = Objects.requireNonNull(stats, "stats").clone();
            moves = Objects.requireNonNull(moves, "moves").clone();
        }

        @Override
        public byte[] pokemon() {
            return pokemon.clone();
        }

        @Override
        public byte[] stats() {
            return stats.clone();
        }

        @Override
        public byte[] moves() {
            return moves.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PartySlotData slot
                    && Arrays.equals(pokemon, slot.pokemon)
                    && Arrays.equals(stats, slot.stats)
                    && Arrays.equals(moves, slot.moves);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(pokemon);
            result = 31 * result + Arrays.hashCode(stats);
            return 31 * result + Arrays.hashCode(moves);
        }
    }
}
