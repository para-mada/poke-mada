package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PokemonSpeciesDexTest {
    @Test
    void resolvesSpeciesAcrossAllSevenGenerations() {
        assertEquals("Bulbasaur", PokemonSpeciesDex.nameOrFallback(1));
        assertEquals("Yungoos", PokemonSpeciesDex.nameOrFallback(734));
        assertEquals("Zeraora", PokemonSpeciesDex.nameOrFallback(807));
    }

    @Test
    void keepsExplicitFallbackForUnknownSpecies() {
        assertEquals("Pokémon #999", PokemonSpeciesDex.nameOrFallback(999));
    }
}
