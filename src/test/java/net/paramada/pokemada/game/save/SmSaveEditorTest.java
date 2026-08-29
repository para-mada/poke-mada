package net.paramada.pokemada.game.save;

import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SmSaveEditorTest {
    @TempDir Path temporary;

    @Test
    void crc16InvertMatchesKnownVector() {
        byte[] vector = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0xB4C8, SmSaveEditor.crc16Invert(vector, 0, vector.length));
    }

    @Test
    void editsNatureAndEvsWithBackupsAndValidChecksums() throws Exception {
        Path main = temporary.resolve("main");
        Files.write(main, saveWithPokemon(25));
        SmSaveEditor editor = new SmSaveEditor();

        SmSaveEditor.Result nature = editor.modifyNature(main, 0, 25, 13);
        assertEquals(13, decrypted(main)[0x1C] & 0xff);
        assertTrue(Files.isRegularFile(nature.backup()));

        SmSaveEditor.Result ev = editor.addEv(main, 0, 25, "attack", 32);
        assertEquals(32, decrypted(main)[0x1F] & 0xff);
        assertTrue(Files.isRegularFile(ev.backup()));
        assertTrue(PokemonCrypto.hasValidStoredDataChecksum(decrypted(main)));
        byte[] save = Files.readAllBytes(main);
        assertEquals(u16(save, 0x6BC00 + 0x14 + 4 * 8 + 6),
                SmSaveEditor.crc16Invert(save, 0x1400, 0x61C));
    }

    @Test
    void rebuildsTheCompleteChecksumTable() {
        byte[] save = new byte[SmSaveEditor.SAVE_SIZE];
        Arrays.fill(save, 0, 0x6BC00, (byte) 0x5A);
        Arrays.fill(save, 0x6BC00, save.length, (byte) 0xA5);

        SmSaveEditor.rebuildChecksums(save);

        int[][] blocks = {
                {0x00000, 0x00DE0}, {0x00E00, 0x0007C}, {0x01000, 0x00014}, {0x01200, 0x000C0},
                {0x01400, 0x0061C}, {0x01C00, 0x00E00}, {0x02A00, 0x00F78}, {0x03A00, 0x00228},
                {0x03E00, 0x00104}, {0x04000, 0x00200}, {0x04200, 0x00020}, {0x04400, 0x00004},
                {0x04600, 0x00058}, {0x04800, 0x005E6}, {0x04E00, 0x36600}, {0x3B400, 0x0572C},
                {0x40C00, 0x00008}, {0x40E00, 0x01080}, {0x42000, 0x01A08}, {0x43C00, 0x06408},
                {0x4A200, 0x06408}, {0x50800, 0x03998}, {0x54200, 0x00100}, {0x54400, 0x00100},
                {0x54600, 0x10528}, {0x64C00, 0x00204}, {0x65000, 0x00B60}, {0x65C00, 0x03F50},
                {0x69C00, 0x00358}, {0x6A000, 0x00728}, {0x6A800, 0x00200}, {0x6AA00, 0x00718},
                {0x6B200, 0x001FC}, {0x6B400, 0x00200}, {0x6B600, 0x00120}, {0x6B800, 0x001C8},
                {0x6BA00, 0x00200}
        };
        for (int block = 0; block < blocks.length; block++) {
            assertEquals(SmSaveEditor.crc16Invert(save, blocks[block][0], blocks[block][1]),
                    u16(save, 0x6BC00 + 0x14 + block * 8 + 6), "bloque " + block);
        }
    }

    @Test
    void replacesAnExistingSignatureBeforeChecksummingMemeCryptoBlock() throws Exception {
        Path main = temporary.resolve("main");
        byte[] save = saveWithPokemon(25);
        Arrays.fill(save, 0x6BB00, 0x6BB80, (byte) 0x5A);
        Files.write(main, save);

        new SmSaveEditor().modifyNature(main, 0, 25, 13);

        byte[] exported = Files.readAllBytes(main);
        SmMemeCrypto.clearSignature(exported); // PKHeX clears MemeCrypto when loading the save.
        assertEquals(SmSaveEditor.crc16Invert(exported, 0x6BA00, 0x200),
                u16(exported, 0x6BC00 + 0x14 + 36 * 8 + 6));
    }

    @Test
    void refusesWrongSpeciesWithoutWritingOrBackingUp() throws Exception {
        Path main = temporary.resolve("main");
        byte[] original = saveWithPokemon(25);
        Files.write(main, original);
        assertThrows(IllegalStateException.class,
                () -> new SmSaveEditor().modifyNature(main, 0, 26, 3));
        assertArrayEquals(original, Files.readAllBytes(main));
        assertEquals(1, Files.list(temporary).count());
    }

    @Test
    void memeCryptoMatchesOfficialPkhexVector() {
        byte[] input = new byte[256];
        for (int i = 0; i < input.length; i++) input[i] = (byte) i;
        SmMemeCrypto.signMemeData(input);
        byte[] expectedSignature = hex(
                "4185f2713d5a8bcffbff47f86867df680354913fefef08d0d0f1054cb054b508" +
                "e7de3aa789ac444d087ab9412cee965a6d38e45ea010c77ff174f8f8d9993aea" +
                "fb9c0616e287b74487e0ff3f1874291b8170c71113971752717f8d188a319ea6");
        assertArrayEquals(expectedSignature, Arrays.copyOfRange(input, 160, 256));
    }

    private static byte[] saveWithPokemon(int species) {
        byte[] save = new byte[SmSaveEditor.SAVE_SIZE];
        byte[] pokemon = new byte[PokemonCrypto.PARTY_SIZE];
        putU32(pokemon, 0, 0x12345678);
        putU16(pokemon, 0x08, species);
        putU16(pokemon, 0x06, PokemonCrypto.calculateStoredDataChecksum(pokemon));
        byte[] encrypted = PokemonCrypto.encrypt(pokemon);
        System.arraycopy(encrypted, 0, save, 0x1400, encrypted.length);
        SmSaveEditor.setPartyBlockChecksum(save);
        return save;
    }

    private static byte[] decrypted(Path main) throws Exception {
        byte[] save = Files.readAllBytes(main);
        return PokemonCrypto.decrypt(Arrays.copyOfRange(save, 0x1400, 0x1400 + 0x104));
    }

    private static int u16(byte[] data, int offset) { return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort()); }
    private static void putU16(byte[] data, int offset, int value) { ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value); }
    private static void putU32(byte[] data, int offset, int value) { ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value); }
    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
