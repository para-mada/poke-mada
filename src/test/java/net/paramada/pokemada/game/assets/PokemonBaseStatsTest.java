package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class PokemonBaseStatsTest {
    @Test
    void returnsAlolaStarterBaseStats() {
        assertArrayEquals(new int[]{55, 55, 50, 50, 42}, PokemonBaseStats.forSpecies(722));
        assertArrayEquals(new int[]{65, 40, 60, 40, 70}, PokemonBaseStats.forSpecies(725));
        assertArrayEquals(new int[]{54, 54, 66, 56, 40}, PokemonBaseStats.forSpecies(728));
    }

    @Test
    void unknownSpeciesDoesNotFallBackToPrivateBattleStats() {
        assertArrayEquals(new int[5], PokemonBaseStats.forSpecies(-1));
    }

    @Test
    void usesGenerationSevenStatsInsteadOfLaterBalanceChanges() {
        assertArrayEquals(new int[]{50, 150, 50, 150, 60}, PokemonBaseStats.forSpecies(681));
    }
}
