package net.paramada.pokemada.battlelog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BattleLogStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void managerDeduplicatesConsecutiveMessagesAndPersistsFinishedBattle() throws Exception {
        BattleLogStore store = new BattleLogStore(temporaryDirectory);
        BattleLogManager manager = new BattleLogManager(store);
        Instant start = Instant.parse("2026-08-23T18:00:00Z");

        manager.begin(start);
        assertTrue(manager.record(start.plusSeconds(1), "¡Es supereficaz!"));
        assertFalse(manager.record(start.plusSeconds(2), " ¡Es supereficaz! "));
        assertTrue(manager.record(start.plusSeconds(3), "El Yungoos enemigo se ha debilitado."));
        manager.finish(start.plusSeconds(5));

        assertFalse(manager.isActive());
        assertEquals(1, manager.recent().size());
        assertEquals(2, manager.recent().getFirst().events().size());

        BattleLogManager reloaded = new BattleLogManager(new BattleLogStore(temporaryDirectory));
        reloaded.loadHistory();
        assertEquals(List.of("¡Es supereficaz!", "El Yungoos enemigo se ha debilitado."),
                reloaded.recent().getFirst().events().stream().map(BattleLogEvent::message).toList());
        assertEquals(1, Files.list(temporaryDirectory).filter(path -> path.toString().endsWith(".battle.log")).count());
    }

    @Test
    void historyKeepsOnlyTheThreeMostRecentBattles() throws Exception {
        BattleLogStore store = new BattleLogStore(temporaryDirectory);
        Instant base = Instant.parse("2026-08-23T18:00:00Z");
        for (int index = 0; index < 4; index++) {
            Instant started = base.plusSeconds(index * 60L);
            store.save(new BattleLogSession("battle" + index, started, started.plusSeconds(10),
                    List.of(new BattleLogEvent(started.plusSeconds(1), "Evento " + index))));
        }

        List<BattleLogSession> recent = store.loadRecent(BattleLogManager.HISTORY_LIMIT);

        assertEquals(List.of("battle3", "battle2", "battle1"),
                recent.stream().map(BattleLogSession::id).toList());
    }

    @Test
    void insertsTurnMarkerBeforeEachSingleBattleDecisionPrompt() {
        BattleLogManager manager = new BattleLogManager(new BattleLogStore(temporaryDirectory));
        Instant start = Instant.parse("2026-08-23T18:00:00Z");
        manager.begin(start);

        assertTrue(manager.record(start.plusSeconds(1), "¿Qué debería hacer Popplio?", true));
        assertFalse(manager.record(start.plusSeconds(2), "¿Qué debería hacer Popplio?", true));
        assertTrue(manager.record(start.plusSeconds(3), "Popplio ha usado Pistola Agua.", true));
        assertTrue(manager.record(start.plusSeconds(4), "¿Qué debería hacer Popplio?", true));

        assertEquals(List.of("— TURNO 1 —", "¿Qué debería hacer Popplio?",
                        "Popplio ha usado Pistola Agua.", "— TURNO 2 —", "¿Qué debería hacer Popplio?"),
                manager.activeEvents().stream().map(BattleLogEvent::message).toList());
    }

    @Test
    void doesNotInsertTurnMarkersForDoubleBattles() {
        BattleLogManager manager = new BattleLogManager(new BattleLogStore(temporaryDirectory));
        Instant start = Instant.parse("2026-08-23T18:00:00Z");
        manager.begin(start);

        manager.record(start.plusSeconds(1), "¿Qué debería hacer Popplio?", false);

        assertEquals(List.of("¿Qué debería hacer Popplio?"),
                manager.activeEvents().stream().map(BattleLogEvent::message).toList());
    }
}
