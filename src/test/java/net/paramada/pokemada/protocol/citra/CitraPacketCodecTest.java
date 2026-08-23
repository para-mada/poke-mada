package net.paramada.pokemada.protocol.citra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CitraPacketCodecTest {
    @Test
    void encodesReadRequestUsingLegacyLittleEndianLayout() {
        byte[] packet = CitraPacketCodec.readMemoryRequest(0x1234_5678L, 0x9abc_def0L, 32);

        assertArrayEquals(new byte[]{
                0x01, 0x00, 0x00, 0x00,
                0x78, 0x56, 0x34, 0x12,
                0x01, 0x00, 0x00, 0x00,
                0x08, 0x00, 0x00, 0x00,
                (byte) 0xf0, (byte) 0xde, (byte) 0xbc, (byte) 0x9a,
                0x20, 0x00, 0x00, 0x00
        }, packet);
    }

    @Test
    void encodesWriteRequestWithAddressSizeAndContents() {
        byte[] packet = CitraPacketCodec.writeMemoryRequest(
                0xffff_ffffL, 0x0102_0304L, new byte[]{0x11, 0x22, 0x33});

        assertArrayEquals(new byte[]{
                0x01, 0x00, 0x00, 0x00,
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                0x02, 0x00, 0x00, 0x00,
                0x0b, 0x00, 0x00, 0x00,
                0x04, 0x03, 0x02, 0x01,
                0x03, 0x00, 0x00, 0x00,
                0x11, 0x22, 0x33
        }, packet);
    }

    @Test
    void decodesAndPreservesUnsignedRequestId() {
        byte[] encoded = CitraPacketCodec.encode(
                0xffff_ffffL, CitraRequestType.READ_MEMORY, new byte[]{5, 6});

        CitraPacket decoded = CitraPacketCodec.decode(encoded);

        assertEquals(CitraPacketCodec.VERSION, decoded.version());
        assertEquals(0xffff_ffffL, decoded.requestId());
        assertEquals(CitraRequestType.READ_MEMORY, decoded.requestType());
        assertArrayEquals(new byte[]{5, 6}, decoded.data());
    }

    @Test
    void decodedPacketDefensivelyCopiesData() {
        byte[] encoded = CitraPacketCodec.encode(1, CitraRequestType.READ_MEMORY, new byte[]{5});
        CitraPacket decoded = CitraPacketCodec.decode(encoded);

        byte[] returnedData = decoded.data();
        returnedData[0] = 99;

        assertArrayEquals(new byte[]{5}, decoded.data());
    }

    @Test
    void rejectsPacketWhoseDeclaredDataSizeDoesNotMatchDatagram() {
        byte[] encoded = CitraPacketCodec.encode(1, CitraRequestType.READ_MEMORY, new byte[]{5});
        encoded[12] = 2;

        assertThrows(IllegalArgumentException.class, () -> CitraPacketCodec.decode(encoded));
    }

    @Test
    void acceptsMadaLimeReadsButPreservesLegacyWriteLimit() {
        assertEquals(24, CitraPacketCodec.readMemoryRequest(1, 0, 1024).length);
        assertThrows(IllegalArgumentException.class,
                () -> CitraPacketCodec.readMemoryRequest(1, 0, 1025));
        assertThrows(IllegalArgumentException.class,
                () -> CitraPacketCodec.writeMemoryRequest(1, 0, new byte[25]));
    }
}
