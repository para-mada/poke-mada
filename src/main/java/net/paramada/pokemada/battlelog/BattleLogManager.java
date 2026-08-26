package net.paramada.pokemada.battlelog;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BattleLogManager {
    public static final int HISTORY_LIMIT = 3;
    private static final String TURN_MARKER_PREFIX = "— TURNO ";
    private final BattleLogStore store;
    private final List<BattleLogEvent> activeEvents = new ArrayList<>();
    private List<BattleLogSession> recent = List.of();
    private String activeId;
    private Instant startedAt;
    private String lastObservedMessage = "";
    private int turnNumber;

    public BattleLogManager(BattleLogStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public synchronized void loadHistory() throws IOException {
        recent = store.loadRecent(HISTORY_LIMIT);
    }

    public synchronized void begin(Instant now) {
        if (isActive()) return;
        startedAt = Objects.requireNonNull(now, "now");
        activeId = UUID.randomUUID().toString().substring(0, 8);
        activeEvents.clear();
        lastObservedMessage = "";
        turnNumber = 0;
    }

    public synchronized boolean record(Instant now, String message) {
        return record(now, message, false);
    }

    public synchronized boolean record(Instant now, String message, boolean singleBattle) {
        return record(now, message, singleBattle, "");
    }

    /**
     * In double battles the game asks once for each active Pokémon. Only the
     * left/primary combatant prompt begins a new shared turn.
     */
    public synchronized boolean record(Instant now, String message, boolean singleBattle,
                                       String primaryPokemonName) {
        if (!isActive()) return false;
        String normalized = BattleMessageSanitizer.sanitize(message);
        if (normalized.isBlank() || normalized.equals(lastObservedMessage)) return false;
        lastObservedMessage = normalized;
        if ((singleBattle && isTurnPrompt(normalized))
                || (!singleBattle && isTurnPromptFor(normalized, primaryPokemonName))) {
            turnNumber++;
            activeEvents.add(new BattleLogEvent(now, TURN_MARKER_PREFIX + turnNumber + " —"));
        }
        activeEvents.add(new BattleLogEvent(now, normalized));
        return true;
    }

    public static boolean isTurnMarker(String message) {
        return message != null && message.startsWith(TURN_MARKER_PREFIX);
    }

    static boolean isTurnPrompt(String message) {
        return message != null && message.matches("(?iu)^¿?qué debería hacer\\s+.+\\?$" );
    }

    static boolean isTurnPromptFor(String message, String pokemonName) {
        if (pokemonName == null || pokemonName.isBlank()) return false;
        return message != null && message.matches("(?iu)^¿?qué debería hacer\\s+"
                + Pattern.quote(pokemonName.trim()) + "\\?$");
    }

    public synchronized Optional<BattleLogSession> finish(Instant now) throws IOException {
        if (!isActive()) return Optional.empty();
        BattleLogSession completed = new BattleLogSession(activeId, startedAt, now, activeEvents);
        if (!completed.events().isEmpty()) {
            store.save(completed);
            recent = store.loadRecent(HISTORY_LIMIT);
        }
        clearActive();
        if (completed.events().isEmpty()) return Optional.empty();
        return Optional.of(completed);
    }

    public synchronized boolean isActive() {
        return activeId != null;
    }

    public synchronized List<BattleLogEvent> activeEvents() {
        return List.copyOf(activeEvents);
    }

    public synchronized Optional<Instant> activeStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public synchronized List<BattleLogSession> recent() {
        return recent;
    }

    private void clearActive() {
        activeId = null;
        startedAt = null;
        activeEvents.clear();
        lastObservedMessage = "";
        turnNumber = 0;
    }
}
