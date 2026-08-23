package net.paramada.pokemada.protocol.citra;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class CitraPacketCodec {
    public static final int VERSION = 1;
    public static final int HEADER_SIZE = 16;
    /** Read-reply size supported by MadaLime's extended RPC contract. */
    public static final int MAX_READ_SIZE = 1024;
    /** Read-reply size used by unmodified Citra/Lime3DS RPC servers. */
    public static final int LEGACY_MAX_READ_SIZE = 32;
    public static final int MEMORY_OPERATION_PREFIX_SIZE = 8;
    /** MadaLime deliberately preserves the original write capability. */
    public static final int MAX_WRITE_CONTENT_SIZE = 24;

    private static final long MAX_UNSIGNED_INT = 0xffff_ffffL;

    private CitraPacketCodec() {
    }

    public static byte[] encode(long requestId, CitraRequestType requestType, byte[] data) {
        requireUnsignedInt(requestId, "requestId");
        Objects.requireNonNull(requestType, "requestType");
        Objects.requireNonNull(data, "data");

        ByteBuffer packet = littleEndianBuffer(HEADER_SIZE + data.length);
        packet.putInt(VERSION);
        packet.putInt((int) requestId);
        packet.putInt(requestType.code());
        packet.putInt(data.length);
        packet.put(data);
        return packet.array();
    }

    public static CitraPacket decode(byte[] datagram) {
        Objects.requireNonNull(datagram, "datagram");
        if (datagram.length < HEADER_SIZE) {
            throw new IllegalArgumentException("Citra packet is shorter than its 16-byte header");
        }

        ByteBuffer packet = ByteBuffer.wrap(datagram).order(ByteOrder.LITTLE_ENDIAN);
        int version = packet.getInt();
        long requestId = Integer.toUnsignedLong(packet.getInt());
        CitraRequestType requestType = CitraRequestType.fromCode(packet.getInt());
        long declaredDataSize = Integer.toUnsignedLong(packet.getInt());
        int actualDataSize = packet.remaining();
        if (declaredDataSize != actualDataSize) {
            throw new IllegalArgumentException(
                    "Citra packet declares %d data bytes but contains %d".formatted(declaredDataSize, actualDataSize));
        }

        byte[] data = new byte[actualDataSize];
        packet.get(data);
        return new CitraPacket(version, requestId, requestType, data);
    }

    public static byte[] readMemoryRequest(long requestId, long address, int size) {
        requireUnsignedInt(address, "address");
        if (size < 1 || size > MAX_READ_SIZE) {
            throw new IllegalArgumentException("Read size must be between 1 and " + MAX_READ_SIZE);
        }

        ByteBuffer data = littleEndianBuffer(MEMORY_OPERATION_PREFIX_SIZE);
        data.putInt((int) address);
        data.putInt(size);
        return encode(requestId, CitraRequestType.READ_MEMORY, data.array());
    }

    public static byte[] writeMemoryRequest(long requestId, long address, byte[] contents) {
        requireUnsignedInt(address, "address");
        Objects.requireNonNull(contents, "contents");
        if (contents.length < 1 || contents.length > MAX_WRITE_CONTENT_SIZE) {
            throw new IllegalArgumentException("Write size must be between 1 and " + MAX_WRITE_CONTENT_SIZE);
        }

        ByteBuffer data = littleEndianBuffer(MEMORY_OPERATION_PREFIX_SIZE + contents.length);
        data.putInt((int) address);
        data.putInt(contents.length);
        data.put(contents);
        return encode(requestId, CitraRequestType.WRITE_MEMORY, data.array());
    }

    private static ByteBuffer littleEndianBuffer(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void requireUnsignedInt(long value, String name) {
        if (value < 0 || value > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException(name + " must fit in an unsigned 32-bit integer");
        }
    }
}
