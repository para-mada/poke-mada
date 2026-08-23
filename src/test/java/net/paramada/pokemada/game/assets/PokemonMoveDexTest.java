package net.paramada.pokemada.game.assets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PokemonMoveDexTest {
    @Test
    void loadsLocalizedMoveInformation() {
        var tackle = PokemonMoveDex.find(33).orElseThrow();
        assertEquals("Placaje", tackle.name());
        assertEquals("Normal", tackle.type());
        assertEquals("físico", tackle.category());
        assertEquals(40, tackle.power());
        assertEquals(35, tackle.pp());
        assertEquals(100, tackle.accuracy());
        assertEquals("Embiste con todo el cuerpo.", tackle.description());
    }

    @Test
    void includesGenerationSevenMoves() {
        var leafage = PokemonMoveDex.find(670).orElseThrow();
        assertEquals("Follaje", leafage.name());
        assertTrue(leafage.description().contains("hojas"));
    }
}
