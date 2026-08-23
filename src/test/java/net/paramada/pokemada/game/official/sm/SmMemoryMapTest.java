package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.official.shared.memory.BattleEnvironment;
import net.paramada.pokemada.game.official.shared.memory.GameMemoryMap;
import net.paramada.pokemada.game.official.shared.memory.GameMemoryReader;
import net.paramada.pokemada.game.official.shared.memory.TeamOwner;
import net.paramada.pokemada.game.memory.MemoryClient;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmMemoryMapTest {
    @Test
    void battleTextConsensusExcludesUnstableRenderBox() {
        assertEquals(2, SmMemoryMap.BATTLE_TEXT_CONSENSUS_ADDRESSES.size());
        assertFalse(SmMemoryMap.BATTLE_TEXT_CONSENSUS_ADDRESSES
                .contains(SmMemoryMap.BATTLE_TEXT_RENDER_BOX_ADDRESS));
        assertEquals(3, SmMemoryMap.BATTLE_TEXT_MIRROR_ADDRESSES.size());
    }

    @Test
    void exposesCanonicalSunMoonPartyLocations() {
        GameMemoryMap map = SmMemoryMap.INSTANCE;

        assertEquals("pokemon-sm", map.gameId());
        assertEquals(0x34195e10L, map.party().address());
        assertEquals(484, map.party().slotStride());
        assertEquals(232, map.party().pokemonDataSize());
        assertEquals(344, map.party().statsOffset());
        assertEquals(0x34195d84L, SmMemoryMap.PARTY_INDEX_ADDRESS);
    }

    @Test
    void modelsGenerationSevenBattleSlotsAndOffsets() {
        GameMemoryMap map = SmMemoryMap.INSTANCE;
        GameMemoryReader reader = new GameMemoryReader(new NoOpMemoryClient(), map);

        assertEquals(0x30002770L + 816L * 12,
                reader.battleSlotAddress(BattleEnvironment.TRAINER, 12));
        assertEquals(562, map.battlePokemon().form() + 1);
        assertEquals(0x1f4, map.battlePokemon().moves().address());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5),
                map.battle(BattleEnvironment.WILD).team(TeamOwner.PLAYER).battleSlots());
        assertEquals(Set.of(12, 13, 14, 15, 16, 17),
                map.battle(BattleEnvironment.WILD).team(TeamOwner.ENEMY).battleSlots());
    }

    @Test
    void wildAndTrainerBattlesUseTheSameGenerationSevenRegion() {
        assertSame(
                SmMemoryMap.INSTANCE.battle(BattleEnvironment.WILD),
                SmMemoryMap.INSTANCE.battle(BattleEnvironment.TRAINER));
        assertThrows(IllegalArgumentException.class,
                () -> SmMemoryMap.INSTANCE.battle(BattleEnvironment.MULTI));
    }

    @Test
    void exposesSunMoonRuntimeBagWithoutClaimingUsumCompatibility() {
        assertEquals(0x330d5934L, SmMemoryMap.INSTANCE.items().bagAnchorAddress());
        assertEquals(0x6b8,
                SmMemoryMap.INSTANCE.items().pocket(GameMemoryMap.BagPocket.KEY_ITEMS).offset());
        assertEquals(0x78,
                SmMemoryMap.INSTANCE.items().pocket(GameMemoryMap.BagPocket.Z_CRYSTALS).length());
        assertEquals(0x330d6714L, SmMemoryMap.BAG_BLOCK_END_EXCLUSIVE);
    }

    private static final class NoOpMemoryClient implements MemoryClient {
        @Override
        public byte[] readMemory(long address, int size) {
            return new byte[size];
        }

        @Override
        public void writeMemory(long address, byte[] data) {
        }

        @Override
        public boolean testConnection() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
