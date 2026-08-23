package net.paramada.pokemada.tools.dump;

import java.util.List;

/** Unique RAM-backed guest mappings from MadaLime's core/memory.h. */
public final class GlobalMemoryDumpProfile {
    public static final String ID = "n3ds-global-1gib-sparse";
    public static final long LOGICAL_SIZE = 0x4000_0000L;

    private GlobalMemoryDumpProfile() {
    }

    public static List<MemoryDumpRegion> regions() {
        return List.of(
                new MemoryDumpRegion("n3ds-extra-ram", 0x1e80_0000L, 0x0040_0000),
                new MemoryDumpRegion("vram", 0x1f00_0000L, 0x0060_0000),
                new MemoryDumpRegion("dsp-ram", 0x1ff0_0000L, 0x0008_0000),
                new MemoryDumpRegion("config-memory", 0x1ff8_0000L, 0x0000_1000),
                new MemoryDumpRegion("shared-page", 0x1ff8_1000L, 0x0000_1000),
                new MemoryDumpRegion("fcram", 0x3000_0000L, 0x1000_0000));
    }
}
