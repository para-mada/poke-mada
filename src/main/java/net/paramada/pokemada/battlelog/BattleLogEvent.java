package net.paramada.pokemada.battlelog;

import java.time.Instant;
import java.util.Objects;

public record BattleLogEvent(Instant timestamp, String message) {
    public BattleLogEvent {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isBlank()) throw new IllegalArgumentException("battle log message must not be blank");
    }
}
