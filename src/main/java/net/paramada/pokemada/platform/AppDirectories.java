package net.paramada.pokemada.platform;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Central location for writable application data. Packaged binaries remain read-only. */
public final class AppDirectories {
    public static final String DATA_DIRECTORY_PROPERTY = "pokemada.data.dir";
    private static final String APPLICATION_DIRECTORY = "PokeMada";

    private AppDirectories() {
    }

    public static Path dataDirectory() {
        String override = System.getProperty(DATA_DIRECTORY_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return resolveDataDirectory(System.getenv(), System.getProperty("os.name", ""),
                Path.of(System.getProperty("user.home")));
    }

    public static Path cacheDirectory() {
        return dataDirectory().resolve("cache");
    }

    static Path resolveDataDirectory(Map<String, String> environment, String osName, Path userHome) {
        if (osName.toLowerCase(Locale.ROOT).contains("win")) {
            String localAppData = environment.get("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Path.of(localAppData).resolve(APPLICATION_DIRECTORY).toAbsolutePath().normalize();
            }
            return userHome.resolve("AppData").resolve("Local").resolve(APPLICATION_DIRECTORY)
                    .toAbsolutePath().normalize();
        }
        String xdgDataHome = environment.get("XDG_DATA_HOME");
        Path base = xdgDataHome == null || xdgDataHome.isBlank()
                ? userHome.resolve(".local").resolve("share")
                : Path.of(xdgDataHome);
        return base.resolve(APPLICATION_DIRECTORY).toAbsolutePath().normalize();
    }
}
