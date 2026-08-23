package net.paramada.pokemada.game.official.shared.memory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record GameMemoryMap(
        String gameId,
        PartyLayout party,
        PokemonOffsets pokemon,
        BattlePokemonOffsets battlePokemon,
        Map<BattleEnvironment, BattleLayout> battles,
        ItemLayout items,
        MessageLayout messages
) {
    public GameMemoryMap {
        gameId = Objects.requireNonNull(gameId, "gameId");
        party = Objects.requireNonNull(party, "party");
        pokemon = Objects.requireNonNull(pokemon, "pokemon");
        battlePokemon = Objects.requireNonNull(battlePokemon, "battlePokemon");
        items = Objects.requireNonNull(items, "items");
        messages = Objects.requireNonNull(messages, "messages");

        EnumMap<BattleEnvironment, BattleLayout> copy = new EnumMap<>(BattleEnvironment.class);
        copy.putAll(Objects.requireNonNull(battles, "battles"));
        battles = Collections.unmodifiableMap(copy);
    }

    public BattleLayout battle(BattleEnvironment environment) {
        BattleLayout layout = battles.get(Objects.requireNonNull(environment, "environment"));
        if (layout == null) {
            throw new IllegalArgumentException(gameId + " has no layout for " + environment);
        }
        return layout;
    }

    public record PartyLayout(
            long address,
            long moveDataAddress,
            int slotStride,
            int pokemonDataSize,
            int statsOffset,
            int statsDataSize,
            int moveDataStride,
            int moveDataSize
    ) {
        public PartyLayout {
            requireAddress(address, "party.address");
            requireAddress(moveDataAddress, "party.moveDataAddress");
            requirePositive(slotStride, "party.slotStride");
            requirePositive(pokemonDataSize, "party.pokemonDataSize");
            requirePositive(statsOffset, "party.statsOffset");
            requirePositive(statsDataSize, "party.statsDataSize");
            requirePositive(moveDataStride, "party.moveDataStride");
            requirePositive(moveDataSize, "party.moveDataSize");
        }
    }

    public record PokemonOffsets(
            int checksum,
            int ability,
            int personalityId,
            int heldItem,
            int nature,
            int form,
            int evHp,
            int evAttack,
            int evDefense,
            int evSpeed,
            int evSpecialAttack,
            int evSpecialDefense,
            int nickname,
            int nicknameLength,
            int individualValues
    ) {
    }

    public record BattlePokemonOffsets(
            int dexNumber,
            int battleSlot,
            int level,
            int form,
            int gender,
            int currentHp,
            StatsOffsets stats,
            BoostOffsets boosts,
            StatusOffsets status,
            int types,
            int heldItem,
            int ability,
            MoveOffsets moves
    ) {
        public BattlePokemonOffsets {
            stats = Objects.requireNonNull(stats, "stats");
            boosts = Objects.requireNonNull(boosts, "boosts");
            status = Objects.requireNonNull(status, "status");
            moves = Objects.requireNonNull(moves, "moves");
        }
    }

    public record StatsOffsets(
            int maxHp,
            int attack,
            int defense,
            int specialAttack,
            int specialDefense,
            int speed
    ) {
    }

    public record BoostOffsets(
            int attack,
            int defense,
            int specialAttack,
            int specialDefense,
            int speed,
            int accuracy,
            int evasion
    ) {
    }

    public record StatusOffsets(int burned, int paralyzed, int asleep, int frozen, int poisoned) {
    }

    public record MoveOffsets(int address, int stride, int ppOffset) {
    }

    public record BattleLayout(
            Map<TeamOwner, BattleTeamLayout> teams,
            CombatLayout combat
    ) {
        public BattleLayout {
            EnumMap<TeamOwner, BattleTeamLayout> copy = new EnumMap<>(TeamOwner.class);
            copy.putAll(Objects.requireNonNull(teams, "teams"));
            teams = Collections.unmodifiableMap(copy);
            combat = Objects.requireNonNull(combat, "combat");
        }

        public BattleTeamLayout team(TeamOwner owner) {
            BattleTeamLayout layout = teams.get(Objects.requireNonNull(owner, "owner"));
            if (layout == null) {
                throw new IllegalArgumentException("No battle-team layout for " + owner);
            }
            return layout;
        }
    }

    public record BattleTeamLayout(
            long address,
            Set<Integer> battleSlots,
            int slotStride,
            int pokemonDataSize,
            int statsDataSize
    ) {
        public BattleTeamLayout {
            requireAddress(address, "battleTeam.address");
            battleSlots = Set.copyOf(Objects.requireNonNull(battleSlots, "battleSlots"));
            requirePositive(slotStride, "battleTeam.slotStride");
            requirePositive(pokemonDataSize, "battleTeam.pokemonDataSize");
            requirePositive(statsDataSize, "battleTeam.statsDataSize");
        }
    }

    public record CombatLayout(
            long address,
            long ppAddress,
            int pokemonStride,
            int frontalPokemonStride,
            int pokemonDataSize,
            long selectedPokemonAddress,
            LogAddresses logs
    ) {
        public CombatLayout {
            requireAddress(address, "combat.address");
            requireAddress(ppAddress, "combat.ppAddress");
            requireAddress(selectedPokemonAddress, "combat.selectedPokemonAddress");
            requirePositive(pokemonStride, "combat.pokemonStride");
            requirePositive(frontalPokemonStride, "combat.frontalPokemonStride");
            requirePositive(pokemonDataSize, "combat.pokemonDataSize");
            logs = Objects.requireNonNull(logs, "logs");
        }
    }

    public record LogAddresses(long combat, long trainer, long move) {
        public LogAddresses {
            requireAddress(combat, "logs.combat");
            requireAddress(trainer, "logs.trainer");
            requireAddress(move, "logs.move");
        }
    }

    public record ItemLayout(
            long moneyAddress,
            long badgesAddress,
            long bagAnchorAddress,
            Map<BagPocket, PocketLayout> pockets
    ) {
        public ItemLayout {
            requireAddress(moneyAddress, "items.moneyAddress");
            requireAddress(badgesAddress, "items.badgesAddress");
            requireAddress(bagAnchorAddress, "items.bagAnchorAddress");
            EnumMap<BagPocket, PocketLayout> copy = new EnumMap<>(BagPocket.class);
            copy.putAll(Objects.requireNonNull(pockets, "pockets"));
            pockets = Collections.unmodifiableMap(copy);
        }

        public PocketLayout pocket(BagPocket pocket) {
            PocketLayout layout = pockets.get(Objects.requireNonNull(pocket, "pocket"));
            if (layout == null) {
                throw new IllegalArgumentException("No item layout for " + pocket);
            }
            return layout;
        }
    }

    public enum BagPocket {
        BERRIES,
        MEDICINE,
        TMS,
        KEY_ITEMS,
        ITEMS,
        Z_CRYSTALS
    }

    public record PocketLayout(int offset, int length) {
        public PocketLayout {
            requireNonNegative(offset, "pocket.offset");
            requirePositive(length, "pocket.length");
        }
    }

    public record MessageLayout(long firstChatAddress, long secondChatAddress, int chatLength) {
        public MessageLayout {
            requireAddress(firstChatAddress, "messages.firstChatAddress");
            requireAddress(secondChatAddress, "messages.secondChatAddress");
            requirePositive(chatLength, "messages.chatLength");
        }
    }

    private static void requireAddress(long address, String name) {
        if (address < 0 || address > 0xffff_ffffL) {
            throw new IllegalArgumentException(name + " must fit in an unsigned 32-bit integer");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
