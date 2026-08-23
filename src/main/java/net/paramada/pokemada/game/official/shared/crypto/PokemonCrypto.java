package net.paramada.pokemada.game.official.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class PokemonCrypto {
    public static final int STORED_SIZE = 232;
    public static final int PARTY_SIZE = 260;

    private static final int HEADER_SIZE = 8;
    private static final int BLOCK_SIZE = 56;
    private static final int BLOCK_COUNT = 4;
    private static final long LCG_MULTIPLIER = 0x41c64e6dL;
    private static final long LCG_INCREMENT = 0x6073L;
    private static final long UINT32_MASK = 0xffff_ffffL;

    private static final int[][] BLOCK_POSITION = {
            {0, 0, 0, 0, 0, 0, 1, 1, 2, 3, 2, 3, 1, 1, 2, 3, 2, 3, 1, 1, 2, 3, 2, 3},
            {1, 1, 2, 3, 2, 3, 0, 0, 0, 0, 0, 0, 2, 3, 1, 1, 3, 2, 2, 3, 1, 1, 3, 2},
            {2, 3, 1, 1, 3, 2, 2, 3, 1, 1, 3, 2, 0, 0, 0, 0, 0, 0, 3, 2, 3, 2, 1, 1},
            {3, 2, 3, 2, 1, 1, 3, 2, 3, 2, 1, 1, 3, 2, 3, 2, 1, 1, 0, 0, 0, 0, 0, 0}
    };

    private PokemonCrypto() {
    }

    public static byte[] decrypt(byte[] encryptedData) {
        requireValidPokemonData(encryptedData);
        long seed = readUnsignedInt(encryptedData, 0);
        int shuffleValue = shuffleValue(seed);

        byte[] decrypted = encryptedData.clone();
        byte[] encryptedBlocks = crypt(encryptedData, seed, HEADER_SIZE, STORED_SIZE);
        byte[] orderedBlocks = shuffle(encryptedBlocks, shuffleValue);
        System.arraycopy(orderedBlocks, 0, decrypted, HEADER_SIZE, orderedBlocks.length);

        if (encryptedData.length > STORED_SIZE) {
            byte[] stats = crypt(encryptedData, seed, STORED_SIZE, encryptedData.length);
            System.arraycopy(stats, 0, decrypted, STORED_SIZE, stats.length);
        }
        return decrypted;
    }

    public static byte[] encrypt(byte[] decryptedData) {
        requireValidPokemonData(decryptedData);
        long seed = readUnsignedInt(decryptedData, 0);
        int shuffleValue = shuffleValue(seed);

        byte[] encrypted = decryptedData.clone();
        byte[] orderedBlocks = new byte[BLOCK_SIZE * BLOCK_COUNT];
        System.arraycopy(decryptedData, HEADER_SIZE, orderedBlocks, 0, orderedBlocks.length);
        byte[] shuffledBlocks = unshuffle(orderedBlocks, shuffleValue);
        byte[] encryptedBlocks = crypt(shuffledBlocks, seed, 0, shuffledBlocks.length);
        System.arraycopy(encryptedBlocks, 0, encrypted, HEADER_SIZE, encryptedBlocks.length);

        if (decryptedData.length > STORED_SIZE) {
            byte[] stats = crypt(decryptedData, seed, STORED_SIZE, decryptedData.length);
            System.arraycopy(stats, 0, encrypted, STORED_SIZE, stats.length);
        }
        return encrypted;
    }

    public static int calculateStoredDataChecksum(byte[] decryptedData) {
        requireValidPokemonData(decryptedData);
        int checksum = 0;
        ByteBuffer words = ByteBuffer.wrap(decryptedData, HEADER_SIZE, STORED_SIZE - HEADER_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN);
        while (words.hasRemaining()) {
            checksum = (checksum + Short.toUnsignedInt(words.getShort())) & 0xffff;
        }
        return checksum;
    }

    public static boolean hasValidStoredDataChecksum(byte[] decryptedData) {
        requireValidPokemonData(decryptedData);
        int storedChecksum = Short.toUnsignedInt(
                ByteBuffer.wrap(decryptedData, 6, Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).getShort());
        return storedChecksum == calculateStoredDataChecksum(decryptedData);
    }

    private static byte[] crypt(byte[] data, long initialSeed, int start, int end) {
        byte[] result = new byte[end - start];
        long seed = initialSeed;
        for (int input = start, output = 0; input < end; input += 2, output += 2) {
            seed = (seed * LCG_MULTIPLIER + LCG_INCREMENT) & UINT32_MASK;
            result[output] = (byte) (data[input] ^ ((seed >>> 16) & 0xff));
            result[output + 1] = (byte) (data[input + 1] ^ ((seed >>> 24) & 0xff));
        }
        return result;
    }

    private static byte[] shuffle(byte[] blocks, int shuffleValue) {
        byte[] result = new byte[blocks.length];
        for (int outputBlock = 0; outputBlock < BLOCK_COUNT; outputBlock++) {
            int inputBlock = BLOCK_POSITION[outputBlock][shuffleValue];
            System.arraycopy(
                    blocks,
                    inputBlock * BLOCK_SIZE,
                    result,
                    outputBlock * BLOCK_SIZE,
                    BLOCK_SIZE);
        }
        return result;
    }

    private static byte[] unshuffle(byte[] orderedBlocks, int shuffleValue) {
        byte[] result = new byte[orderedBlocks.length];
        for (int orderedBlock = 0; orderedBlock < BLOCK_COUNT; orderedBlock++) {
            int shuffledBlock = BLOCK_POSITION[orderedBlock][shuffleValue];
            System.arraycopy(
                    orderedBlocks,
                    orderedBlock * BLOCK_SIZE,
                    result,
                    shuffledBlock * BLOCK_SIZE,
                    BLOCK_SIZE);
        }
        return result;
    }

    private static int shuffleValue(long seed) {
        return (int) (((seed >>> 13) & 31) % 24);
    }

    private static long readUnsignedInt(byte[] data, int offset) {
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(data, offset, Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private static void requireValidPokemonData(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length < STORED_SIZE) {
            throw new IllegalArgumentException("Pokémon data must contain at least 232 bytes");
        }
        if ((data.length & 1) != 0) {
            throw new IllegalArgumentException("Pokémon data length must be even");
        }
    }
}
