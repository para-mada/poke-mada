package net.paramada.pokemada.game.official.xy;

import net.paramada.pokemada.game.official.shared.memory.BattleEnvironment;
import net.paramada.pokemada.game.official.shared.memory.GameMemoryMap;
import net.paramada.pokemada.game.official.shared.memory.TeamOwner;

import java.util.Map;
import java.util.Set;

import net.paramada.pokemada.game.official.shared.memory.GameMemoryMap.BagPocket;

public final class XyMemoryMap {
    // Canonical values come from the newer legacy RamData.js. The older flat
    // romData.js disagrees on several PP/log addresses and is not merged silently.
    public static final GameMemoryMap INSTANCE = create();

    private XyMemoryMap() {
    }

    private static GameMemoryMap create() {
        GameMemoryMap.PartyLayout party = new GameMemoryMap.PartyLayout(
                0x08ce1ce8L, 0x0820430cL, 484, 232, 344, 22, 580, 56);

        GameMemoryMap.PokemonOffsets pokemon = new GameMemoryMap.PokemonOffsets(
                0x06, 0x14, 0x18, 0x0a, 0x1c, 0x1d,
                0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23,
                0x40, 26, 0x74);

        GameMemoryMap.BattlePokemonOffsets battlePokemon = new GameMemoryMap.BattlePokemonOffsets(
                0x04, 0x11, 0x10, 0x14b, 0xfb, 0x08,
                new GameMemoryMap.StatsOffsets(0x06, 0xee, 0xf0, 0xf2, 0xf4, 0xf6),
                new GameMemoryMap.BoostOffsets(0xfc, 0xfd, 0xfe, 0xff, 0x100, 0x101, 0x102),
                new GameMemoryMap.StatusOffsets(0x24, 0x18, 0x1c, 0x20, 0x28),
                0xf8, 0x0a, 0x146,
                new GameMemoryMap.MoveOffsets(0x10e, 14, 0x110));

        GameMemoryMap.BattleTeamLayout playerWild = team(0x08804a70L, Set.of(0, 1, 2, 3, 4, 5));
        GameMemoryMap.BattleTeamLayout playerTrainer = team(0x08803f28L, Set.of(0, 1, 2, 3, 4, 5));
        GameMemoryMap.BattleTeamLayout enemyWild = team(
                0x08803eccL, Set.of(12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23));
        GameMemoryMap.BattleTeamLayout enemyTrainer = team(
                0x08804accL, Set.of(12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23));
        GameMemoryMap.BattleTeamLayout ally = team(0, Set.of(6, 7, 8, 9, 10, 11));

        Map<BattleEnvironment, GameMemoryMap.BattleLayout> battles = Map.of(
                BattleEnvironment.WILD, battle(playerWild, enemyWild, ally,
                        combat(0x08203ed0L, 0x0820430cL, 0x08523114L, 0x08523c94L, 0x0845c004L)),
                BattleEnvironment.TRAINER, battle(playerTrainer, enemyTrainer, ally,
                        combat(0x082059e0L, 0x08205e1cL, 0x08523114L, 0x08523c94L, 0x0845c004L)),
                BattleEnvironment.MULTI, battle(playerTrainer, enemyTrainer, ally,
                        combat(0x08209d98L, 0x08205e1cL, 0, 0, 0x084cf064L))
        );

        GameMemoryMap.ItemLayout items = new GameMemoryMap.ItemLayout(
                0x08c6a69cL,
                0x08c6a6a0L,
                0x08c6a69cL,
                Map.of(
                        BagPocket.BERRIES, new GameMemoryMap.PocketLayout(9952, 9952),
                        BagPocket.MEDICINE, new GameMemoryMap.PocketLayout(10208, 256),
                        BagPocket.TMS, new GameMemoryMap.PocketLayout(10640, 432),
                        BagPocket.KEY_ITEMS, new GameMemoryMap.PocketLayout(11016, 376),
                        BagPocket.ITEMS, new GameMemoryMap.PocketLayout(12616, 1600)
                ));

        return new GameMemoryMap(
                "pokemon-xy",
                party,
                pokemon,
                battlePokemon,
                battles,
                items,
                new GameMemoryMap.MessageLayout(0x08804906L, 0x08805df8L, 500));
    }

    private static GameMemoryMap.BattleTeamLayout team(long address, Set<Integer> slots) {
        return new GameMemoryMap.BattleTeamLayout(address, slots, 484, 232, 22);
    }

    private static GameMemoryMap.BattleLayout battle(
            GameMemoryMap.BattleTeamLayout player,
            GameMemoryMap.BattleTeamLayout enemy,
            GameMemoryMap.BattleTeamLayout ally,
            GameMemoryMap.CombatLayout combat
    ) {
        return new GameMemoryMap.BattleLayout(
                Map.of(TeamOwner.PLAYER, player, TeamOwner.ENEMY, enemy, TeamOwner.ALLY, ally), combat);
    }

    private static GameMemoryMap.CombatLayout combat(
            long address,
            long ppAddress,
            long combatLog,
            long trainerLog,
            long moveLog
    ) {
        return new GameMemoryMap.CombatLayout(
                address,
                ppAddress,
                580,
                235_568,
                332,
                0x084208c8L,
                new GameMemoryMap.LogAddresses(combatLog, trainerLog, moveLog));
    }
}
