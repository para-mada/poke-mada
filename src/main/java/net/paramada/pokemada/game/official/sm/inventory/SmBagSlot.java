package net.paramada.pokemada.game.official.sm.inventory;

public record SmBagSlot(SmBagPocket pocket, int physicalSlot, Gen7BagEntry entry) {
    public SmBagSlot {
        if (physicalSlot < 0 || physicalSlot >= pocket.slots()) {
            throw new IllegalArgumentException("physicalSlot outside pouch");
        }
    }

    public boolean quantityWithinNormalLimit() {
        return entry.count() <= pocket.expectedMaxCount(entry.itemId());
    }
}
