package net.paramada.pokemada.game.official.sm.inventory;

/** Transport-independent decoder for one packed Generation VII inventory value. */
public record Gen7BagEntry(int itemId, int count, int freeSpaceIndex, boolean isNew) {
    public static Gen7BagEntry decode(int raw) {
        return new Gen7BagEntry(
                raw & 0x3ff,
                (raw >>> 10) & 0x3ff,
                (raw >>> 20) & 0x3ff,
                (raw & 0x4000_0000) != 0);
    }

    public boolean occupied() {
        return itemId != 0 && count > 0;
    }
}
