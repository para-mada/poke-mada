package net.paramada.pokemada.game.official.sm.inventory;

import net.paramada.pokemada.game.assets.PokemonItemDex;
import net.paramada.pokemada.protocol.citra.CitraUdpClient;

/** Independent read-only live validation: prints item_name and quantity for every physical pouch. */
public final class SmBagDump {
    private SmBagDump() {
    }

    public static void main(String[] args) throws Exception {
        try (CitraUdpClient citra = new CitraUdpClient()) {
            SmBagSnapshot bag = new SmBagReader(citra).read();
            for (SmBagPocket pocket : SmBagPocket.values()) {
                System.out.println("[" + pocket.displayName() + "]");
                boolean empty = true;
                for (SmBagSlot slot : bag.byPocket().get(pocket)) {
                    Gen7BagEntry entry = slot.entry();
                    if (!entry.occupied()) continue;
                    empty = false;
                    String name = PokemonItemDex.find(entry.itemId())
                            .map(PokemonItemDex.ItemInfo::name)
                            .orElse("Item #" + entry.itemId());
                    String warning = slot.quantityWithinNormalLimit() ? "" : "  [cantidad fuera del límite normal]";
                    System.out.printf("%s, %d%s%n", name, entry.count(), warning);
                }
                if (empty) System.out.println("(vacío)");
                System.out.println();
            }
        }
    }
}
