package net.paramada.pokemada.features.vial;

import net.paramada.pokemada.platform.AppDirectories;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persistent charge counter plus conservative full-party healing transition detector. */
public final class PokeVial {
    public static final int DEFAULT_MAX_CHARGES = 3;
    private final Path stateFile;
    private final int maxCharges;
    private int charges;
    private PartyState previous;
    private boolean ignoreNextFullRestore;

    public PokeVial() {
        this(AppDirectories.dataDirectory().resolve("poke-vial.properties"),
                Integer.getInteger("pokemada.poke-vial.max-charges", DEFAULT_MAX_CHARGES));
    }

    public PokeVial(Path stateFile, int maxCharges) {
        if (maxCharges < 1) throw new IllegalArgumentException("maxCharges must be positive");
        this.stateFile = stateFile;
        this.maxCharges = maxCharges;
        this.charges = load();
    }

    public synchronized int charges() { return charges; }
    public int maxCharges() { return maxCharges; }
    public synchronized boolean available() { return charges > 0; }

    public synchronized void consume() throws IOException {
        if (charges <= 0) throw new IllegalStateException("No quedan cargas");
        charges--;
        ignoreNextFullRestore = true;
        save();
    }

    public synchronized boolean observe(PartyState current, boolean inBattle) throws IOException {
        boolean transition = !inBattle && previous != null && previous.sameRoster(current)
                && previous.needsHealing() && current.fullyRestored();
        previous = current;
        if (ignoreNextFullRestore && current.fullyRestored()) {
            ignoreNextFullRestore = false;
            return false;
        }
        if (!transition) return false;
        if (charges == maxCharges) return false;
        charges = maxCharges;
        save();
        return true;
    }

    private int load() {
        if (!Files.isRegularFile(stateFile)) return maxCharges;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            properties.load(input);
            return Math.clamp(Integer.parseInt(properties.getProperty("charges")), 0, maxCharges);
        } catch (IOException | RuntimeException ignored) {
            return maxCharges;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(stateFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("charges", Integer.toString(charges));
        properties.setProperty("maxCharges", Integer.toString(maxCharges));
        Path temporary = Files.createTempFile(stateFile.getParent(), "poke-vial-", ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "PokeMada Poke Vial");
        }
        try {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record PartyState(int[] species, int[] currentHp, int[] maxHp, int[] status,
                             int[][] currentPp, int[][] maxPp) {
        public PartyState {
            species = species.clone(); currentHp = currentHp.clone(); maxHp = maxHp.clone(); status = status.clone();
            currentPp = deepCopy(currentPp); maxPp = deepCopy(maxPp);
        }
        public boolean needsHealing() { return occupied() > 0 && !fullyRestored(); }
        public boolean fullyRestored() {
            if (occupied() == 0) return false;
            for (int i = 0; i < species.length; i++) if (species[i] != 0) {
                if (currentHp[i] != maxHp[i] || status[i] != 0) return false;
                for (int m = 0; m < currentPp[i].length; m++) if (currentPp[i][m] != maxPp[i][m]) return false;
            }
            return true;
        }
        public boolean sameRoster(PartyState other) { return java.util.Arrays.equals(species, other.species); }
        private int occupied() { int count = 0; for (int value : species) if (value != 0) count++; return count; }
        private static int[][] deepCopy(int[][] values) {
            int[][] copy = new int[values.length][];
            for (int i = 0; i < values.length; i++) copy[i] = values[i].clone();
            return copy;
        }
    }
}
