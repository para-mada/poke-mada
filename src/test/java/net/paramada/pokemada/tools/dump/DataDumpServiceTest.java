package net.paramada.pokemada.tools.dump;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataDumpServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesRawRegionsAndReproducibilityManifest() throws Exception {
        List<MemoryDumpRegion> regions = List.of(
                new MemoryDumpRegion("first", 0x1000, 4),
                new MemoryDumpRegion("second", 0x2000, 3));

        DataDumpService.DumpResult result = new DataDumpService().dump(
                temporaryDirectory.resolve("capture"), "test-profile", regions,
                (address, length) -> {
                    byte[] bytes = new byte[length];
                    for (int index = 0; index < length; index++) bytes[index] = (byte) (address + index);
                    return bytes;
                });

        assertEquals(2, result.regions().size());
        assertArrayEquals(new byte[]{0, 1, 2, 3},
                Files.readAllBytes(result.outputDirectory().resolve(result.regions().getFirst().filename())));
        String manifest = Files.readString(result.manifest());
        assertTrue(manifest.contains("\"profile\": \"test-profile\""));
        assertTrue(manifest.contains("\"address\": \"0x00001000\""));
        assertTrue(manifest.contains("\"readOnly\": true"));
        assertTrue(manifest.contains(result.regions().getFirst().sha256()));
    }

    @Test
    void sunMoonProfileCoversAllKnownRuntimeAreas() {
        List<MemoryDumpRegion> regions = SmDataDumpProfile.regions();

        assertEquals(13, regions.size());
        assertTrue(regions.stream().anyMatch(region -> region.name().equals("party") && region.length() == 2_904));
        assertTrue(regions.stream().anyMatch(region -> region.name().equals("battle") && region.length() == 14_434));
        assertTrue(regions.stream().anyMatch(region -> region.name().equals("bag") && region.length() == 0xde0));
    }

    @Test
    void globalDumpPreservesGuestOffsetsInSparseLogicalImage() throws Exception {
        List<MemoryDumpRegion> regions = List.of(
                new MemoryDumpRegion("low", 16, 4),
                new MemoryDumpRegion("high", 3_000, 3));
        Path output = temporaryDirectory.resolve("global");

        GlobalDataDumpService.GlobalDumpResult result = new GlobalDataDumpService().dump(
                output, 4_096, regions,
                (address, length) -> {
                    byte[] contents = new byte[length];
                    for (int index = 0; index < length; index++) contents[index] = (byte) (address + index);
                    return contents;
                }, 2);

        assertEquals(4_096, Files.size(result.image()));
        byte[] image = Files.readAllBytes(result.image());
        assertArrayEquals(new byte[]{16, 17, 18, 19}, java.util.Arrays.copyOfRange(image, 16, 20));
        assertArrayEquals(new byte[]{(byte) 3_000, (byte) 3_001, (byte) 3_002},
                java.util.Arrays.copyOfRange(image, 3_000, 3_003));
        assertTrue(Files.readString(result.manifest()).contains("\"logicalSize\": 4096"));
    }
}
