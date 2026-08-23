package net.paramada.pokemada.tools.dump;

import net.paramada.pokemada.game.memory.MemoryAddressSpace;
import net.paramada.pokemada.protocol.citra.CitraPacketCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Writes immutable raw captures. The service exposes no memory-write operation. */
public final class DataDumpService {
    private static final HexFormat HEX = HexFormat.of();

    public DumpResult dump(Path outputDirectory, String profile, List<MemoryDumpRegion> regions,
                           MemoryReader reader) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(reader, "reader");
        if (regions.isEmpty()) throw new IllegalArgumentException("at least one dump region is required");

        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);
        List<DumpedRegion> dumped = new ArrayList<>();
        int index = 1;
        for (MemoryDumpRegion region : regions) {
            byte[] contents = reader.read(region.address(), region.length());
            if (contents.length != region.length()) {
                throw new IOException("Expected %d bytes for %s but received %d"
                        .formatted(region.length(), region.name(), contents.length));
            }
            String filename = "%02d-%s-0x%08x-%d.bin"
                    .formatted(index++, region.name(), region.address(), region.length());
            Path file = output.resolve(filename);
            Files.write(file, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            dumped.add(new DumpedRegion(region, filename, sha256(contents)));
            System.out.printf("%-24s 0x%08x  %6d bytes%n", region.name(), region.address(), region.length());
        }

        Instant createdAt = Instant.now();
        Path manifest = output.resolve("manifest.json");
        Files.writeString(manifest, manifest(profile, createdAt, dumped), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new DumpResult(output, manifest, createdAt, List.copyOf(dumped));
    }

    private static String manifest(String profile, Instant createdAt, List<DumpedRegion> dumped) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\n")
                .append("  \"schemaVersion\": 1,\n")
                .append("  \"profile\": ").append(jsonString(profile)).append(",\n")
                .append("  \"createdAt\": ").append(jsonString(createdAt.toString())).append(",\n")
                .append("  \"readOnly\": true,\n")
                .append("  \"addressSpace\": ")
                .append(jsonString(MemoryAddressSpace.NINTENDO_3DS_GUEST_VIRTUAL.name())).append(",\n")
                .append("  \"transport\": \"Citra/Lime3DS UDP RPC v1\",\n")
                .append("  \"preferredReadPayload\": ").append(CitraPacketCodec.MAX_READ_SIZE).append(",\n")
                .append("  \"legacyReadFallback\": ").append(CitraPacketCodec.LEGACY_MAX_READ_SIZE).append(",\n")
                .append("  \"regions\": [\n");
        for (int index = 0; index < dumped.size(); index++) {
            DumpedRegion item = dumped.get(index);
            json.append("    {\"name\": ").append(jsonString(item.region().name()))
                    .append(", \"address\": ").append(jsonString("0x%08x".formatted(item.region().address())))
                    .append(", \"length\": ").append(item.region().length())
                    .append(", \"file\": ").append(jsonString(item.filename()))
                    .append(", \"sha256\": ").append(jsonString(item.sha256())).append('}');
            if (index + 1 < dumped.size()) json.append(',');
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private static String sha256(byte[] contents) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append("\\u%04x".formatted((int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.append('"').toString();
    }

    @FunctionalInterface
    public interface MemoryReader {
        byte[] read(long address, int length) throws IOException;
    }

    public record DumpedRegion(MemoryDumpRegion region, String filename, String sha256) {
    }

    public record DumpResult(Path outputDirectory, Path manifest, Instant createdAt,
                             List<DumpedRegion> regions) {
    }
}
