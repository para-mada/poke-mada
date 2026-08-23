package net.paramada.pokemada.tools.dump;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds a resumable sparse guest-address image while only reading real RAM-backed mappings. */
public final class GlobalDataDumpService {
    static final int STREAM_BLOCK_SIZE = 1024 * 1024;
    private static final String PARTIAL_FILE = "global-guest-1gib.sparse.bin.partial";
    private static final String FINAL_FILE = "global-guest-1gib.sparse.bin";
    private static final String CHECKPOINT_FILE = "global-progress.txt";

    public GlobalDumpResult dump(Path outputDirectory, long logicalSize, List<MemoryDumpRegion> regions,
                                 DataDumpService.MemoryReader reader) throws IOException {
        return dump(outputDirectory, logicalSize, regions, reader, STREAM_BLOCK_SIZE);
    }

    GlobalDumpResult dump(Path outputDirectory, long logicalSize, List<MemoryDumpRegion> regions,
                          DataDumpService.MemoryReader reader, int streamBlockSize) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(reader, "reader");
        if (logicalSize <= 0 || regions.isEmpty()) throw new IllegalArgumentException("global layout is empty");
        if (streamBlockSize <= 0) throw new IllegalArgumentException("streamBlockSize must be positive");
        for (MemoryDumpRegion region : regions) {
            if (region.address() + region.length() > logicalSize) {
                throw new IllegalArgumentException(region.name() + " exceeds the logical image");
            }
        }

        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path partial = output.resolve(PARTIAL_FILE);
        Path checkpoint = output.resolve(CHECKPOINT_FILE);
        Path completed = output.resolve(FINAL_FILE);
        if (Files.exists(completed)) throw new IOException("Global dump already exists: " + completed);

        Progress progress = loadProgress(partial, checkpoint);
        Set<StandardOpenOption> options = Files.exists(partial)
                ? Set.of(StandardOpenOption.WRITE)
                : Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SPARSE);

        long totalBytes = regions.stream().mapToLong(MemoryDumpRegion::length).sum();
        long completedBytes = bytesBefore(regions, progress.regionIndex()) + progress.regionOffset();
        try (FileChannel image = FileChannel.open(partial, options)) {
            for (int regionIndex = progress.regionIndex(); regionIndex < regions.size(); regionIndex++) {
                MemoryDumpRegion region = regions.get(regionIndex);
                int regionOffset = regionIndex == progress.regionIndex() ? progress.regionOffset() : 0;
                System.out.printf("[%d/%d] %s 0x%08x (%d MiB)%n", regionIndex + 1, regions.size(),
                        region.name(), region.address(), Math.max(1, region.length() / (1024 * 1024)));
                long nextReport = completedBytes + 16L * 1024 * 1024;
                while (regionOffset < region.length()) {
                    int blockLength = Math.min(streamBlockSize, region.length() - regionOffset);
                    byte[] block = reader.read(region.address() + regionOffset, blockLength);
                    if (block.length != blockLength) {
                        throw new IOException("Short global read at 0x%08x".formatted(region.address() + regionOffset));
                    }
                    image.position(region.address() + regionOffset);
                    writeFully(image, ByteBuffer.wrap(block));
                    regionOffset += blockLength;
                    completedBytes += blockLength;
                    saveProgress(checkpoint, new Progress(regionIndex, regionOffset));
                    if (completedBytes >= nextReport || regionOffset == region.length()) {
                        System.out.printf("  progreso: %.1f%% (%d / %d MiB)%n",
                                completedBytes * 100.0 / totalBytes,
                                completedBytes / (1024 * 1024), totalBytes / (1024 * 1024));
                        nextReport = completedBytes + 16L * 1024 * 1024;
                    }
                }
                saveProgress(checkpoint, new Progress(regionIndex + 1, 0));
            }
            image.position(logicalSize - 1);
            writeFully(image, ByteBuffer.wrap(new byte[]{0}));
        }

        moveCompleted(partial, completed);
        Files.deleteIfExists(checkpoint);
        List<GlobalRegionResult> results = hashes(completed, regions);
        Path manifest = output.resolve("global-manifest.json");
        Files.writeString(manifest, manifest(logicalSize, results), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new GlobalDumpResult(output, completed, manifest, logicalSize, totalBytes, results);
    }

    private static Progress loadProgress(Path partial, Path checkpoint) throws IOException {
        boolean hasPartial = Files.exists(partial);
        boolean hasCheckpoint = Files.exists(checkpoint);
        if (!hasPartial && !hasCheckpoint) return new Progress(0, 0);
        if (hasPartial != hasCheckpoint) {
            throw new IOException("Incomplete global dump has no matching checkpoint in " + partial.getParent());
        }
        String[] values = Files.readString(checkpoint).trim().split(":", -1);
        if (values.length != 2) throw new IOException("Invalid global dump checkpoint");
        try {
            return new Progress(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid global dump checkpoint", exception);
        }
    }

    private static void saveProgress(Path checkpoint, Progress progress) throws IOException {
        Files.writeString(checkpoint, progress.regionIndex() + ":" + progress.regionOffset(),
                StandardCharsets.US_ASCII, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static long bytesBefore(List<MemoryDumpRegion> regions, int regionIndex) {
        long total = 0;
        for (int index = 0; index < Math.min(regionIndex, regions.size()); index++) total += regions.get(index).length();
        return total;
    }

    private static void writeFully(FileChannel channel, ByteBuffer data) throws IOException {
        while (data.hasRemaining()) channel.write(data);
    }

    private static void moveCompleted(Path partial, Path completed) throws IOException {
        try {
            Files.move(partial, completed, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(partial, completed);
        }
    }

    private static List<GlobalRegionResult> hashes(Path image, List<MemoryDumpRegion> regions) throws IOException {
        List<GlobalRegionResult> results = new ArrayList<>();
        try (FileChannel channel = FileChannel.open(image, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(STREAM_BLOCK_SIZE);
            for (MemoryDumpRegion region : regions) {
                MessageDigest digest = sha256();
                long position = region.address();
                int remaining = region.length();
                while (remaining > 0) {
                    buffer.clear().limit(Math.min(buffer.capacity(), remaining));
                    int read = channel.read(buffer, position);
                    if (read < 0) throw new IOException("Unexpected end of sparse image");
                    digest.update(buffer.array(), 0, read);
                    position += read;
                    remaining -= read;
                }
                results.add(new GlobalRegionResult(region, HexFormat.of().formatHex(digest.digest())));
            }
        }
        return List.copyOf(results);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String manifest(long logicalSize, List<GlobalRegionResult> results) {
        StringBuilder json = new StringBuilder(1024);
        json.append("{\n  \"schemaVersion\": 1,\n")
                .append("  \"profile\": \"").append(GlobalMemoryDumpProfile.ID).append("\",\n")
                .append("  \"createdAt\": \"").append(Instant.now()).append("\",\n")
                .append("  \"readOnly\": true,\n")
                .append("  \"sparse\": true,\n")
                .append("  \"logicalSize\": ").append(logicalSize).append(",\n")
                .append("  \"regions\": [\n");
        for (int index = 0; index < results.size(); index++) {
            GlobalRegionResult result = results.get(index);
            json.append("    {\"name\": \"").append(result.region().name())
                    .append("\", \"address\": \"0x%08x\"".formatted(result.region().address()))
                    .append(", \"length\": ").append(result.region().length())
                    .append(", \"sha256\": \"").append(result.sha256()).append("\"}");
            if (index + 1 < results.size()) json.append(',');
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private record Progress(int regionIndex, int regionOffset) {
        private Progress {
            if (regionIndex < 0 || regionOffset < 0) throw new IllegalArgumentException("negative progress");
        }
    }

    public record GlobalRegionResult(MemoryDumpRegion region, String sha256) {
    }

    public record GlobalDumpResult(Path outputDirectory, Path image, Path manifest, long logicalSize,
                                   long capturedBytes, List<GlobalRegionResult> regions) {
    }
}
