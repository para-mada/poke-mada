package net.paramada.pokemada.features.vial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PokeVialTest {
    @TempDir Path temporaryDirectory;

    @Test
    void consumesAndPersistsCharges() throws Exception {
        Path state = temporaryDirectory.resolve("vial.properties");
        PokeVial vial = new PokeVial(state, 3);

        vial.consume();

        assertEquals(2, vial.charges());
        assertEquals(2, new PokeVial(state, 3).charges());
    }

    @Test
    void refillsAfterACompleteOutOfBattleRestore() throws Exception {
        PokeVial vial = new PokeVial(temporaryDirectory.resolve("vial.properties"), 3);
        vial.consume();
        vial = new PokeVial(temporaryDirectory.resolve("vial.properties"), 3);
        vial.observe(party(10, 20, 0, 4, 10), false);

        assertTrue(vial.observe(party(20, 20, 0, 10, 10), false));
        assertEquals(3, vial.charges());
    }

    @Test
    void doesNotTreatItsOwnHealingAsACenterVisit() throws Exception {
        PokeVial vial = new PokeVial(temporaryDirectory.resolve("vial.properties"), 3);
        vial.observe(party(10, 20, 1, 4, 10), false);
        vial.consume();

        assertFalse(vial.observe(party(20, 20, 0, 10, 10), false));
        assertEquals(2, vial.charges());
    }

    @Test
    void doesNotRefillDuringBattle() throws Exception {
        PokeVial vial = new PokeVial(temporaryDirectory.resolve("vial.properties"), 3);
        vial.consume();
        vial.observe(party(10, 20, 0, 4, 10), false);

        assertFalse(vial.observe(party(20, 20, 0, 10, 10), true));
        assertEquals(2, vial.charges());
    }

    private static PokeVial.PartyState party(int hp, int maxHp, int status, int pp, int maxPp) {
        return new PokeVial.PartyState(
                new int[]{722, 0, 0, 0, 0, 0},
                new int[]{hp, 0, 0, 0, 0, 0},
                new int[]{maxHp, 0, 0, 0, 0, 0},
                new int[]{status, 0, 0, 0, 0, 0},
                new int[][]{{pp, 0, 0, 0}, {}, {}, {}, {}, {}},
                new int[][]{{maxPp, 0, 0, 0}, {}, {}, {}, {}, {}});
    }
}
