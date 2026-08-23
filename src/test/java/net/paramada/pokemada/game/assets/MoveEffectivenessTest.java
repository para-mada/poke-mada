package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MoveEffectivenessTest {
    @Test
    void calculatesSingleAndDualTypeEffectiveness() {
        var water = PokemonMoveDex.find(55).orElseThrow();
        assertEquals(2.0, MoveEffectiveness.against(water, 9, 9).orElseThrow());

        var electric = PokemonMoveDex.find(85).orElseThrow();
        assertEquals(4.0, MoveEffectiveness.against(electric, 10, 2).orElseThrow());
    }

    @Test
    void hidesMultiplierForStatusAndFixedDamageMoves() {
        assertTrue(MoveEffectiveness.against(PokemonMoveDex.find(45).orElseThrow(), 10, 10).isEmpty());
        assertTrue(MoveEffectiveness.against(PokemonMoveDex.find(69).orElseThrow(), 0, 0).isEmpty());
    }
}
