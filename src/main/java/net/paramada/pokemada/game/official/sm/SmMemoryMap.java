package net.paramada.pokemada.game.official.sm;

import net.paramada.pokemada.game.official.shared.memory.BattleEnvironment;
import net.paramada.pokemada.game.official.shared.memory.GameMemoryMap;
import net.paramada.pokemada.game.official.shared.memory.TeamOwner;
import net.paramada.pokemada.game.official.sm.inventory.SmBagPocket;

import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Pokemon Sun/Moon memory locations used by the Citra scripts in
 * Readek/Pokemon-Stream-Tool.
 */
public final class SmMemoryMap {
    public static final long PARTY_INDEX_ADDRESS = 0x34195d84L;
    public static final long ACTIVE_POKEMON_ADDRESS = 0x30e93f98L;
    public static final int ACTIVE_POKEMON_STRIDE = 20_528;
    public static final int ACTIVE_POKEMON_COUNT = 4;
    public static final long ENEMY_TRAINER_NAME_ADDRESS = 0x30034a74L;
    public static final int ENEMY_TRAINER_NAME_LENGTH = 26;
    public static final int ENEMY_TRAINER_TITLE_OFFSET = 140;
    public static final int ENEMY_TRAINER_TITLE_LENGTH = 36;
    public static final long BATTLE_TEXT_PRIMARY_ADDRESS = 0x302e41d4L;
    /**
     * Backing buffer for the text box currently being rendered. It can contain a partial message,
     * so it is retained for research dumps but deliberately excluded from runtime consensus.
     */
    public static final long BATTLE_TEXT_RENDER_BOX_ADDRESS = 0x30387bd8L;
    public static final long BATTLE_TEXT_SECONDARY_ADDRESS = 0x30439a88L;
    /** All known text regions, including the unstable render-box buffer, for diagnostic dumps. */
    public static final List<Long> BATTLE_TEXT_MIRROR_ADDRESSES = List.of(
            BATTLE_TEXT_PRIMARY_ADDRESS, BATTLE_TEXT_RENDER_BOX_ADDRESS, BATTLE_TEXT_SECONDARY_ADDRESS);
    /** Stable mirrors that must agree before a message is accepted by the battle log. */
    public static final List<Long> BATTLE_TEXT_CONSENSUS_ADDRESSES = List.of(
            BATTLE_TEXT_PRIMARY_ADDRESS, BATTLE_TEXT_SECONDARY_ADDRESS);
    public static final int BATTLE_TEXT_MIRROR_LENGTH = 0x100;
    /** Pokémon Sun/Moon only. Unencrypted runtime MyItem block in guest virtual memory. */
    public static final long BAG_BLOCK_ADDRESS = 0x330d5934L;
    public static final int BAG_BLOCK_LENGTH = 0x0de0;
    public static final long BAG_BLOCK_END_EXCLUSIVE = BAG_BLOCK_ADDRESS + BAG_BLOCK_LENGTH;

    public static final GameMemoryMap INSTANCE = create();

    private static final long PARTY_ADDRESS = 0x34195e10L;
    private static final long BATTLE_ADDRESS = 0x30002770L;

    private SmMemoryMap() {
    }

    private static GameMemoryMap create() {
        GameMemoryMap.PartyLayout party = new GameMemoryMap.PartyLayout(
                PARTY_ADDRESS,
                PARTY_ADDRESS,
                484,
                232,
                344,
                22,
                484,
                232);

        // The encrypted party structure is shared by generations VI and VII.
        GameMemoryMap.PokemonOffsets pokemon = new GameMemoryMap.PokemonOffsets(
                0x06, 0x14, 0x18, 0x0a, 0x1c, 0x1d,
                0x1e, 0x1f, 0x20, 0x21, 0x22, 0x23,
                0x40, 24, 0x74);

        GameMemoryMap.BattlePokemonOffsets battlePokemon = new GameMemoryMap.BattlePokemonOffsets(
                0x04, 0x11, 0x10, 0x231, 0x1df, 0x08,
                new GameMemoryMap.StatsOffsets(0x06, 0x1d2, 0x1d4, 0x1d6, 0x1d8, 0x1da),
                new GameMemoryMap.BoostOffsets(0x1e2, 0x1e3, 0x1e4, 0x1e5, 0x1e6, 0x1e7, 0x1e8),
                new GameMemoryMap.StatusOffsets(0x38, 0x20, 0x28, 0x30, 0x40),
                0x1dc, 0x0a, 0x22c,
                new GameMemoryMap.MoveOffsets(0x1f4, 14, 0x1f6));

        GameMemoryMap.BattleTeamLayout player = team(Set.of(0, 1, 2, 3, 4, 5));
        GameMemoryMap.BattleTeamLayout ally = team(Set.of(6, 7, 8, 9, 10, 11));
        GameMemoryMap.BattleTeamLayout enemy = team(Set.of(12, 13, 14, 15, 16, 17));
        GameMemoryMap.BattleLayout sharedBattle = new GameMemoryMap.BattleLayout(
                Map.of(TeamOwner.PLAYER, player, TeamOwner.ALLY, ally, TeamOwner.ENEMY, enemy),
                new GameMemoryMap.CombatLayout(
                        BATTLE_ADDRESS,
                        BATTLE_ADDRESS,
                        816,
                        ACTIVE_POKEMON_STRIDE,
                        562,
                        ACTIVE_POKEMON_ADDRESS,
                        new GameMemoryMap.LogAddresses(0, 0, 0)));

        // Runtime MyItem layout for Pokémon Sun/Moon. Do not reuse for Ultra Sun/Ultra Moon.
        GameMemoryMap.ItemLayout items = new GameMemoryMap.ItemLayout(
                0, 0, BAG_BLOCK_ADDRESS,
                Map.of(
                        GameMemoryMap.BagPocket.ITEMS, pocket(SmBagPocket.ITEMS),
                        GameMemoryMap.BagPocket.KEY_ITEMS, pocket(SmBagPocket.KEY_ITEMS),
                        GameMemoryMap.BagPocket.TMS, pocket(SmBagPocket.TMS_HMS),
                        GameMemoryMap.BagPocket.MEDICINE, pocket(SmBagPocket.MEDICINE),
                        GameMemoryMap.BagPocket.BERRIES, pocket(SmBagPocket.BERRIES),
                        GameMemoryMap.BagPocket.Z_CRYSTALS, pocket(SmBagPocket.Z_CRYSTALS)));
        GameMemoryMap.MessageLayout unavailableMessages = new GameMemoryMap.MessageLayout(0, 0, 1);

        return new GameMemoryMap(
                "pokemon-sm",
                party,
                pokemon,
                battlePokemon,
                Map.of(
                        BattleEnvironment.WILD, sharedBattle,
                        BattleEnvironment.TRAINER, sharedBattle),
                items,
                unavailableMessages);
    }

    private static GameMemoryMap.BattleTeamLayout team(Set<Integer> slots) {
        return new GameMemoryMap.BattleTeamLayout(BATTLE_ADDRESS, slots, 816, 562, 22);
    }

    private static GameMemoryMap.PocketLayout pocket(SmBagPocket pocket) {
        return new GameMemoryMap.PocketLayout(pocket.offset(), pocket.byteLength());
    }
}
