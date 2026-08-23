package net.paramada.pokemada.game.official.sm.inventory;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.official.sm.SmMemoryMap;

import java.io.IOException;
import java.util.Objects;

/** Read-only live reader. No decryption or save serialization is involved. */
public final class SmBagReader {
    private final MemoryClient memory;

    public SmBagReader(MemoryClient memory) {
        this.memory = Objects.requireNonNull(memory, "memory");
    }

    public SmBagSnapshot read() throws IOException {
        return SmBagParser.parse(memory.readMemory(SmMemoryMap.BAG_BLOCK_ADDRESS,
                SmMemoryMap.BAG_BLOCK_LENGTH));
    }
}
