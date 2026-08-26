package net.paramada.pokemada.game.save;

import net.paramada.pokemada.game.PokemonGameConfig;
import net.paramada.pokemada.server.ServerClient;
import net.paramada.pokemada.server.ServerSettings;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Watches a game's save and uploads a stable snapshot after each actual change. */
public final class SaveFileWatcher implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(SaveFileWatcher.class.getName());
    private static final Duration SETTLE_DELAY = Duration.ofMillis(750);

    private final PokemonGameConfig game;
    private final Supplier<ServerSettings> session;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile WatchService watchService;
    private volatile byte[] lastUploaded;

    public SaveFileWatcher(PokemonGameConfig game) {
        this(game, ServerSettings::load);
    }

    SaveFileWatcher(PokemonGameConfig game, Supplier<ServerSettings> session) {
        this.game = Objects.requireNonNull(game, "game");
        this.session = Objects.requireNonNull(session, "session");
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        Thread.startVirtualThread(this::watch);
    }

    private void watch() {
        Path directory = game.save().directory();
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            watchService = watcher;
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            while (running.get()) {
                WatchKey key = watcher.take();
                boolean saveChanged = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context() instanceof Path changed
                            && changed.getFileName().toString().equals(game.save().fileName())) {
                        saveChanged = true;
                    }
                }
                if (!key.reset()) break;
                if (saveChanged) uploadWhenStable();
            }
        } catch (java.nio.file.ClosedWatchServiceException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Could not watch " + game.name() + " save at " + directory, failure);
        } finally {
            running.set(false);
            watchService = null;
        }
    }

    private void uploadWhenStable() {
        try {
            Thread.sleep(SETTLE_DELAY);
            Path file = game.save().file();
            if (!Files.isRegularFile(file)) return;
            byte[] snapshot = Files.readAllBytes(file);
            if (snapshot.length == 0 || Arrays.equals(snapshot, lastUploaded)) return;
            ServerSettings settings = session.get();
            if (settings.token().isBlank()) return;
            upload(settings, snapshot).whenComplete((ignored, failure) -> {
                if (failure == null) {
                    lastUploaded = snapshot;
                    LOGGER.log(System.Logger.Level.INFO, game.name() + " save uploaded");
                } else {
                    LOGGER.log(System.Logger.Level.WARNING, "Could not upload " + game.name() + " save", failure);
                }
            });
        } catch (IOException failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read " + game.name() + " save", failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    CompletableFuture<Void> upload(ServerSettings settings, byte[] snapshot) {
        return new ServerClient(settings.baseUrl()).uploadSave(settings.token(), game.save().uploadEndpoint(),
                game.save().fileName(), snapshot);
    }

    @Override
    public void close() {
        running.set(false);
        WatchService watcher = watchService;
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException ignored) {
                // Already closing.
            }
        }
    }
}
