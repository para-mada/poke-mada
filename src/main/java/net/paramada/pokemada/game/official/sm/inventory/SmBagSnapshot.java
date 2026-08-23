package net.paramada.pokemada.game.official.sm.inventory;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SmBagSnapshot(Map<SmBagPocket, List<SmBagSlot>> byPocket) {
    public SmBagSnapshot {
        EnumMap<SmBagPocket, List<SmBagSlot>> copy = new EnumMap<>(SmBagPocket.class);
        for (SmBagPocket pocket : SmBagPocket.values()) {
            copy.put(pocket, List.copyOf(byPocket.getOrDefault(pocket, List.of())));
        }
        byPocket = Map.copyOf(copy);
    }

    /** Item identity is stable even when bag sorting moves its physical slot. */
    public Map<Integer, SmBagSlot> occupiedByItemId() {
        Map<Integer, SmBagSlot> result = new LinkedHashMap<>();
        for (SmBagPocket pocket : SmBagPocket.values()) {
            for (SmBagSlot slot : byPocket.get(pocket)) {
                if (slot.entry().occupied()) result.put(slot.entry().itemId(), slot);
            }
        }
        return Map.copyOf(result);
    }
}
