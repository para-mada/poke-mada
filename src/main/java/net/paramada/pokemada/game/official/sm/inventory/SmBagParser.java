package net.paramada.pokemada.game.official.sm.inventory;

import net.paramada.pokemada.game.official.sm.SmMemoryMap;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class SmBagParser {
    private SmBagParser() {
    }

    public static SmBagSnapshot parse(byte[] block) {
        if (block.length != SmMemoryMap.BAG_BLOCK_LENGTH) {
            throw new IllegalArgumentException("SM MyItem block must be exactly 0xDE0 bytes");
        }
        ByteBuffer data = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        EnumMap<SmBagPocket, List<SmBagSlot>> result = new EnumMap<>(SmBagPocket.class);
        for (SmBagPocket pocket : SmBagPocket.values()) {
            List<SmBagSlot> slots = new ArrayList<>(pocket.slots());
            for (int slot = 0; slot < pocket.slots(); slot++) {
                int raw = data.getInt(pocket.offset() + slot * Integer.BYTES);
                slots.add(new SmBagSlot(pocket, slot, Gen7BagEntry.decode(raw)));
            }
            result.put(pocket, slots);
        }
        return new SmBagSnapshot(result);
    }
}
