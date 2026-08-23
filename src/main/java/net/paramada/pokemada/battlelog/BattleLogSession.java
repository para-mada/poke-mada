package net.paramada.pokemada.battlelog;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record BattleLogSession(String id, Instant startedAt, Instant endedAt, List<BattleLogEvent> events) {
    public BattleLogSession {
        id = Objects.requireNonNull(id, "id");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        endedAt = Objects.requireNonNull(endedAt, "endedAt");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        if (endedAt.isBefore(startedAt)) throw new IllegalArgumentException("battle log ends before it starts");
    }
}
