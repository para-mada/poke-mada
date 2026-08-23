package net.paramada.pokemada.game.official.shared.memory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MemoryTextDecoder {
    private MemoryTextDecoder() {
    }

    public static String decodeUtf16Le(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        int end = bytes.length - (bytes.length % 2);
        for (int index = 0; index + 1 < end; index += 2) {
            if (bytes[index] == 0 && bytes[index + 1] == 0) {
                end = index;
                break;
            }
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_16LE)
                .replace('\n', ' ')
                .strip();
    }
}
