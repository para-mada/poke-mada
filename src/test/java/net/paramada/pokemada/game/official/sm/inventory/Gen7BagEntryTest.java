package net.paramada.pokemada.game.official.sm.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Gen7BagEntryTest {
    @Test
    void decodesAllDocumentedBitFieldsAndIgnoresReservedBit() {
        int raw = 25 | (999 << 10) | (513 << 20) | 0x4000_0000 | 0x8000_0000;
        Gen7BagEntry entry = Gen7BagEntry.decode(raw);

        assertEquals(25, entry.itemId());
        assertEquals(999, entry.count());
        assertEquals(513, entry.freeSpaceIndex());
        assertTrue(entry.isNew());
        assertTrue(entry.occupied());
    }

    @Test
    void requiresBothItemIdAndPositiveCountToBeOccupied() {
        assertFalse(Gen7BagEntry.decode(0).occupied());
        assertFalse(Gen7BagEntry.decode(25).occupied());
        assertFalse(Gen7BagEntry.decode(4 << 10).occupied());
    }
}
