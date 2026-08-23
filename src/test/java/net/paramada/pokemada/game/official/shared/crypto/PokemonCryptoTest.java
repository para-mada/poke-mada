package net.paramada.pokemada.game.official.shared.crypto;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PokemonCryptoTest {
    private static final byte[] LEGACY_ENCRYPTED_FIXTURE = HexFormat.of().parseHex(
            "785634129fc4e90e425397264d35f1c2dfa692524385bc5b9fdbc29045f88996" +
            "2ca6af821d40b31b1e4705028636c478b7c61840a9e8d57ba795f6afe753c2a4" +
            "4c3a8add4d389b771eb4206a33ae1964ae0c5bf5d45eb704fd70e1769bb0ba2c" +
            "148a061309ba76809f341e4e20106bc0e0c411a09fcc444cc5f607eacce070ebe" +
            "9d5dce5f3ec61e2c34046f980ca6398c87ec5afce6078b361e97b64bdae7e9f" +
            "45f51cd0861256fbd4bc805edec0b3ad19ef9616f32c2dc885276d3d625acf6c" +
            "e3eb10c1954f9446d7ad06e53d7bb5e92de100ab4a465f06f3c27ef3b53fae9" +
            "64672b0692a0e3ac5e2b33786ad9591623fc632b2e3e51cbbff7b2230255829f" +
            "68c460fe2");

    @Test
    void encryptionMatchesLegacyJavascriptFixture() {
        assertArrayEquals(LEGACY_ENCRYPTED_FIXTURE, PokemonCrypto.encrypt(fixtureData()));
    }

    @Test
    void decryptsLegacyJavascriptFixture() {
        assertArrayEquals(fixtureData(), PokemonCrypto.decrypt(LEGACY_ENCRYPTED_FIXTURE));
    }

    @Test
    void roundTripsEveryShufflePermutationForStoredAndPartyData() {
        for (int shuffleValue = 0; shuffleValue < 24; shuffleValue++) {
            assertRoundTrip(dataForShuffleValue(PokemonCrypto.STORED_SIZE, shuffleValue));
            assertRoundTrip(dataForShuffleValue(PokemonCrypto.PARTY_SIZE, shuffleValue));
            assertRoundTrip(dataForShuffleValue(332, shuffleValue));
        }
    }

    @Test
    void calculatesChecksumOverDecryptedStoredBlocks() {
        byte[] data = fixtureData();

        assertEquals(0x8820, PokemonCrypto.calculateStoredDataChecksum(data));
        assertFalse(PokemonCrypto.hasValidStoredDataChecksum(data));

        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putShort(6, (short) 0x8820);
        assertTrue(PokemonCrypto.hasValidStoredDataChecksum(data));
    }

    @Test
    void rejectsIncompleteOrOddSizedData() {
        assertThrows(IllegalArgumentException.class, () -> PokemonCrypto.decrypt(new byte[231]));
        assertThrows(IllegalArgumentException.class, () -> PokemonCrypto.encrypt(new byte[233]));
    }

    private static void assertRoundTrip(byte[] decrypted) {
        assertArrayEquals(decrypted, PokemonCrypto.decrypt(PokemonCrypto.encrypt(decrypted)));
    }

    private static byte[] fixtureData() {
        byte[] data = new byte[PokemonCrypto.PARTY_SIZE];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) (index * 37 + 11);
        }
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 0x1234_5678);
        return data;
    }

    private static byte[] dataForShuffleValue(int size, int shuffleValue) {
        byte[] data = new byte[size];
        for (int index = 0; index < data.length; index++) {
            data[index] = (byte) (index * 19 + shuffleValue);
        }
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(0, shuffleValue << 13);
        return data;
    }
}
