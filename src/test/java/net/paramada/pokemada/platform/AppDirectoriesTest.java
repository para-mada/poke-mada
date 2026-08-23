package net.paramada.pokemada.platform;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AppDirectoriesTest {
    @Test
    void usesLocalAppDataOnWindows() {
        Path result = AppDirectories.resolveDataDirectory(
                Map.of("LOCALAPPDATA", "C:\\Users\\trainer\\AppData\\Local"),
                "Windows 11", Path.of("C:\\Users\\trainer"));

        assertEquals(Path.of("C:\\Users\\trainer\\AppData\\Local", "PokeMada")
                .toAbsolutePath().normalize(), result);
    }

    @Test
    void fallsBackToTheWindowsLocalProfileDirectory() {
        Path home = Path.of("C:\\Users\\trainer");
        assertEquals(home.resolve("AppData/Local/PokeMada").toAbsolutePath().normalize(),
                AppDirectories.resolveDataDirectory(Map.of(), "Windows 11", home));
    }

    @Test
    void usesXdgDataHomeAwayFromWindows() {
        assertEquals(Path.of("/var/data/PokeMada").toAbsolutePath().normalize(),
                AppDirectories.resolveDataDirectory(Map.of("XDG_DATA_HOME", "/var/data"),
                        "Linux", Path.of("/home/trainer")));
    }
}
