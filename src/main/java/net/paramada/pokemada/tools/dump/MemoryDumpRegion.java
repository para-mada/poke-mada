package net.paramada.pokemada.tools.dump;

import java.util.Objects;

public record MemoryDumpRegion(String name, long address, int length) {
    private static final long MAX_UNSIGNED_INT = 0xffff_ffffL;

    public MemoryDumpRegion {
        name = Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("region name must use lowercase letters, digits and hyphens");
        }
        if (address < 0 || address > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException("region address must fit in an unsigned 32-bit integer");
        }
        if (length <= 0 || address > MAX_UNSIGNED_INT - (length - 1L)) {
            throw new IllegalArgumentException("region must be a non-empty unsigned 32-bit memory range");
        }
    }
}
