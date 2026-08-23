package net.paramada.pokemada.tools.dump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataDumperTest {
    @Test
    void parsesConnectionAndAdditionalHexRange() {
        DataDumper.Options options = DataDumper.Options.parse(new String[]{
                "--host", "127.0.0.2", "--port", "46000", "--timeout", "8",
                "--range", "research:0x330d5934:0xde0"});

        assertEquals("127.0.0.2", options.host());
        assertEquals(46_000, options.port());
        assertEquals(8, options.timeoutSeconds());
        assertEquals(new MemoryDumpRegion("research", 0x330d5934L, 0xde0),
                options.additionalRegions().getFirst());
    }

    @Test
    void rejectsMalformedOrWritableLookingArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> DataDumper.Options.parse(new String[]{"--range", "missing-fields"}));
        assertThrows(IllegalArgumentException.class,
                () -> DataDumper.Options.parse(new String[]{"--write", "0x1000"}));
    }

    @Test
    void enablesGlobalSparseModeExplicitly() {
        DataDumper.Options options = DataDumper.Options.parse(new String[]{"--global"});

        assertEquals(true, options.global());
    }
}
