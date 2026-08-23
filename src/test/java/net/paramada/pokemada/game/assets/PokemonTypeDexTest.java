package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class PokemonTypeDexTest {
    @Test
    void includesGenerationSevenStarters() {
        assertArrayEquals(new int[]{11, 2}, PokemonTypeDex.forSpecies(722));
        assertArrayEquals(new int[]{9, 9}, PokemonTypeDex.forSpecies(725));
        assertArrayEquals(new int[]{10, 10}, PokemonTypeDex.forSpecies(728));
    }
}
