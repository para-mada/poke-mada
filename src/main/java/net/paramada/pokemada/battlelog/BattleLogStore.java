package net.paramada.pokemada.battlelog;

import net.paramada.pokemada.platform.AppDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BattleLogStore {
    private static final String HEADER = "POKEMADA_BATTLE_LOG_V1";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS", Locale.ROOT).withZone(ZoneOffset.UTC);
    private final Path directory;

    public BattleLogStore() {
        this(AppDirectories.dataDirectory().resolve("battle-logs"));
    }

    BattleLogStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Path save(BattleLogSession session) throws IOException {
        Files.createDirectories(directory);
        String filename = FILE_TIME.format(session.startedAt()) + "-" + session.id() + ".battle.log";
        Path destination = directory.resolve(filename);
        Path temporary = Files.createTempFile(directory, filename, ".tmp");
        try {
            Files.writeString(temporary, serialize(session), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            move(temporary, destination);
            return destination;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public List<BattleLogSession> loadRecent(int limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("limit must not be negative");
        if (!Files.isDirectory(directory) || limit == 0) return List.of();
        try (var paths = Files.list(directory)) {
            List<Path> files = paths.filter(path -> path.getFileName().toString().endsWith(".battle.log"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .limit(limit)
                    .toList();
            List<BattleLogSession> sessions = new ArrayList<>();
            for (Path file : files) {
                try {
                    sessions.add(parse(Files.readAllLines(file, StandardCharsets.UTF_8)));
                } catch (IllegalArgumentException malformed) {
                    System.getLogger(BattleLogStore.class.getName())
                            .log(System.Logger.Level.WARNING, "Ignoring malformed battle log " + file, malformed);
                }
            }
            return List.copyOf(sessions);
        }
    }

    public Path directory() {
        return directory;
    }

    private static String serialize(BattleLogSession session) {
        StringBuilder text = new StringBuilder(HEADER).append('\n')
                .append("id\t").append(escape(session.id())).append('\n')
                .append("started\t").append(session.startedAt()).append('\n')
                .append("ended\t").append(session.endedAt()).append('\n');
        for (BattleLogEvent event : session.events()) {
            text.append("event\t").append(event.timestamp()).append('\t')
                    .append(escape(event.message())).append('\n');
        }
        return text.toString();
    }

    private static BattleLogSession parse(List<String> lines) {
        if (lines.size() < 4 || !HEADER.equals(lines.getFirst())) {
            throw new IllegalArgumentException("unknown battle log format");
        }
        String id = field(lines.get(1), "id");
        Instant started = Instant.parse(field(lines.get(2), "started"));
        Instant ended = Instant.parse(field(lines.get(3), "ended"));
        List<BattleLogEvent> events = new ArrayList<>();
        for (int index = 4; index < lines.size(); index++) {
            String[] parts = lines.get(index).split("\\t", 3);
            if (parts.length != 3 || !"event".equals(parts[0])) continue;
            String message = BattleMessageSanitizer.sanitize(unescape(parts[2]));
            if (!message.isBlank()) events.add(new BattleLogEvent(Instant.parse(parts[1]), message));
        }
        return new BattleLogSession(unescape(id), started, ended, events);
    }

    private static String field(String line, String expected) {
        String[] parts = line.split("\\t", 2);
        if (parts.length != 2 || !expected.equals(parts[0])) {
            throw new IllegalArgumentException("missing " + expected);
        }
        return parts[1];
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (escaped) {
                result.append(switch (character) {
                    case 't' -> '\t';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    default -> character;
                });
                escaped = false;
            } else if (character == '\\') escaped = true;
            else result.append(character);
        }
        if (escaped) result.append('\\');
        return result.toString();
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }
}
