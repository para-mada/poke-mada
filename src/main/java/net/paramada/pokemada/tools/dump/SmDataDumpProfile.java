package net.paramada.pokemada.tools.dump;

import net.paramada.pokemada.game.official.sm.SmMemoryMap;

import java.util.ArrayList;
import java.util.List;

/** Raw runtime regions for Pokémon Sun/Moon only. Never reuse this profile for US/UM. */
public final class SmDataDumpProfile {
    public static final String ID = "pokemon-sm";

    private SmDataDumpProfile() {
    }

    public static List<MemoryDumpRegion> regions() {
        var map = SmMemoryMap.INSTANCE;
        var party = map.party();
        var combat = map.battle(net.paramada.pokemada.game.official.shared.memory.BattleEnvironment.WILD)
                .combat();
        int battleLength = 17 * combat.pokemonStride() + combat.pokemonDataSize();

        List<MemoryDumpRegion> regions = new ArrayList<>();
        regions.add(new MemoryDumpRegion("party-index", SmMemoryMap.PARTY_INDEX_ADDRESS, 1));
        regions.add(new MemoryDumpRegion("party", party.address(), party.slotStride() * 6));
        regions.add(new MemoryDumpRegion("battle", combat.address(), battleLength));
        regions.add(new MemoryDumpRegion("enemy-trainer-name", SmMemoryMap.ENEMY_TRAINER_NAME_ADDRESS,
                SmMemoryMap.ENEMY_TRAINER_NAME_LENGTH));
        regions.add(new MemoryDumpRegion("enemy-trainer-title",
                SmMemoryMap.ENEMY_TRAINER_NAME_ADDRESS + SmMemoryMap.ENEMY_TRAINER_TITLE_OFFSET,
                SmMemoryMap.ENEMY_TRAINER_TITLE_LENGTH));
        regions.add(new MemoryDumpRegion("bag", SmMemoryMap.BAG_BLOCK_ADDRESS, SmMemoryMap.BAG_BLOCK_LENGTH));

        String[] activeNames = {"active-player-one", "active-enemy-one", "active-player-two", "active-enemy-two"};
        for (int index = 0; index < SmMemoryMap.ACTIVE_POKEMON_COUNT; index++) {
            regions.add(new MemoryDumpRegion(activeNames[index],
                    SmMemoryMap.ACTIVE_POKEMON_ADDRESS + (long) SmMemoryMap.ACTIVE_POKEMON_STRIDE * index, 2));
        }
        String[] battleTextNames = {
                "battle-text-stable-primary", "battle-text-render-box-unstable", "battle-text-stable-secondary"};
        for (int index = 0; index < SmMemoryMap.BATTLE_TEXT_MIRROR_ADDRESSES.size(); index++) {
            regions.add(new MemoryDumpRegion(battleTextNames[index],
                    SmMemoryMap.BATTLE_TEXT_MIRROR_ADDRESSES.get(index),
                    SmMemoryMap.BATTLE_TEXT_MIRROR_LENGTH));
        }
        return List.copyOf(regions);
    }
}
