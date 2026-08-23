package net.paramada.pokemada.game.official.sm.inventory;

import net.paramada.pokemada.game.official.shared.memory.GameMemoryMap;

/** Physical pouches in the unencrypted Pokémon Sun/Moon MyItem block. */
public enum SmBagPocket {
    ITEMS(0x000, 430, 999, GameMemoryMap.BagPocket.ITEMS, "Items (incluye Poké Balls)"),
    KEY_ITEMS(0x6b8, 184, 1, GameMemoryMap.BagPocket.KEY_ITEMS, "Objetos clave"),
    TMS_HMS(0x998, 108, 1, GameMemoryMap.BagPocket.TMS, "MT/MO"),
    MEDICINE(0xb48, 64, 999, GameMemoryMap.BagPocket.MEDICINE, "Medicina"),
    BERRIES(0xc48, 72, 999, GameMemoryMap.BagPocket.BERRIES, "Bayas"),
    Z_CRYSTALS(0xd68, 30, 1, GameMemoryMap.BagPocket.Z_CRYSTALS, "Cristales Z");

    private final int offset;
    private final int slots;
    private final int normalMaxCount;
    private final GameMemoryMap.BagPocket sharedPocket;
    private final String displayName;

    SmBagPocket(int offset, int slots, int normalMaxCount,
                GameMemoryMap.BagPocket sharedPocket, String displayName) {
        this.offset = offset;
        this.slots = slots;
        this.normalMaxCount = normalMaxCount;
        this.sharedPocket = sharedPocket;
        this.displayName = displayName;
    }

    public int offset() { return offset; }
    public int slots() { return slots; }
    public int byteLength() { return slots * Integer.BYTES; }
    public int normalMaxCount() { return normalMaxCount; }
    public GameMemoryMap.BagPocket sharedPocket() { return sharedPocket; }
    public String displayName() { return displayName; }

    public int expectedMaxCount(int itemId) {
        return this == KEY_ITEMS && itemId == 797 ? 2 : normalMaxCount;
    }
}
