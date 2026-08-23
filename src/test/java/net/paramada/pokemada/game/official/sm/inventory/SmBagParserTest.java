package net.paramada.pokemada.game.official.sm.inventory;

import net.paramada.pokemada.game.official.sm.SmMemoryMap;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmBagParserTest {
    @Test
    void scansEveryPhysicalSlotAcrossAllPouches() {
        byte[] block = new byte[SmMemoryMap.BAG_BLOCK_LENGTH];
        put(block, SmBagPocket.ITEMS, 429, 4, 12, 31, true);
        put(block, SmBagPocket.KEY_ITEMS, 5, 797, 2, 0, false);
        put(block, SmBagPocket.TMS_HMS, 107, 328, 1, 0, false);
        put(block, SmBagPocket.MEDICINE, 40, 17, 8, 9, false);
        put(block, SmBagPocket.BERRIES, 71, 149, 27, 4, false);
        put(block, SmBagPocket.Z_CRYSTALS, 29, 776, 1, 0, true);

        SmBagSnapshot bag = SmBagParser.parse(block);

        for (SmBagPocket pocket : SmBagPocket.values()) {
            assertEquals(pocket.slots(), bag.byPocket().get(pocket).size());
        }
        assertEquals(12, bag.occupiedByItemId().get(4).entry().count());
        assertEquals(SmBagPocket.BERRIES, bag.occupiedByItemId().get(149).pocket());
        assertTrue(bag.occupiedByItemId().get(797).quantityWithinNormalLimit());
    }

    @Test
    void changingOnlyPackedCountChangesDecodedCountWithoutDecryption() {
        byte[] first = new byte[SmMemoryMap.BAG_BLOCK_LENGTH];
        byte[] second = new byte[SmMemoryMap.BAG_BLOCK_LENGTH];
        put(first, SmBagPocket.ITEMS, 17, 4, 3, 0, false);
        put(second, SmBagPocket.ITEMS, 17, 4, 9, 0, false);

        assertEquals(3, SmBagParser.parse(first).occupiedByItemId().get(4).entry().count());
        assertEquals(9, SmBagParser.parse(second).occupiedByItemId().get(4).entry().count());
    }

    @Test
    void reportsAbnormalQuantityButDoesNotDiscardReadOnlyData() {
        byte[] block = new byte[SmMemoryMap.BAG_BLOCK_LENGTH];
        put(block, SmBagPocket.KEY_ITEMS, 0, 500, 2, 0, false);
        SmBagSlot slot = SmBagParser.parse(block).occupiedByItemId().get(500);
        assertEquals(2, slot.entry().count());
        assertFalse(slot.quantityWithinNormalLimit());
    }

    @Test
    void rejectsAnythingOtherThanTheExactSmBlockLength() {
        assertThrows(IllegalArgumentException.class, () -> SmBagParser.parse(new byte[0xde0 - 1]));
    }

    private static void put(byte[] block, SmBagPocket pocket, int slot, int itemId,
                            int count, int freeSpaceIndex, boolean isNew) {
        int raw = itemId | (count << 10) | (freeSpaceIndex << 20) | (isNew ? 0x4000_0000 : 0);
        ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(pocket.offset() + slot * Integer.BYTES, raw);
    }
}
