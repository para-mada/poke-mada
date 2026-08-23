package net.paramada.pokemada.protocol.citra;

import net.paramada.pokemada.game.memory.MemoryClient;
import net.paramada.pokemada.game.memory.MemoryAddressSpace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

public final class CitraUdpClient implements MemoryClient {
    /** The UDP server contract accepts 32-bit Nintendo 3DS guest virtual addresses unchanged. */
    public static final MemoryAddressSpace ADDRESS_SPACE = MemoryAddressSpace.NINTENDO_3DS_GUEST_VIRTUAL;
    public static final int DEFAULT_PORT = 45_987;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private static final int MAX_DATAGRAM_SIZE = 65_507;
    private static final long MAX_UNSIGNED_INT = 0xffff_ffffL;

    private final DatagramSocket socket;
    private final Duration timeout;
    private long nextRequestId;
    private volatile boolean closed;

    public CitraUdpClient() throws IOException {
        this("localhost", DEFAULT_PORT, DEFAULT_TIMEOUT);
    }

    public CitraUdpClient(String host, int port) throws IOException {
        this(host, port, DEFAULT_TIMEOUT);
    }

    public CitraUdpClient(String host, int port, Duration timeout) throws IOException {
        Objects.requireNonNull(host, "host");
        this.timeout = requireValidTimeout(timeout);
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }

        socket = new DatagramSocket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port));
        } catch (IOException | RuntimeException exception) {
            socket.close();
            throw exception;
        }
        nextRequestId = Integer.toUnsignedLong(new SecureRandom().nextInt());
    }

    @Override
    public synchronized byte[] readMemory(long address, int size) throws IOException {
        ensureOpen();
        requireMemoryRange(address, size);
        if (size == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream(size);
        long currentAddress = address;
        int remaining = size;
        while (remaining > 0) {
            int chunkSize = Math.min(remaining, CitraPacketCodec.MAX_REQUEST_DATA_SIZE);
            long requestId = nextRequestId();
            byte[] request = CitraPacketCodec.readMemoryRequest(requestId, currentAddress, chunkSize);
            send(request);

            CitraPacket response = awaitResponse(requestId, CitraRequestType.READ_MEMORY);
            byte[] responseData = response.data();
            if (responseData.length != chunkSize) {
                throw new IOException(
                        "Citra returned %d bytes for a %d-byte read".formatted(responseData.length, chunkSize));
            }

            result.write(responseData);
            currentAddress += chunkSize;
            remaining -= chunkSize;
        }
        return result.toByteArray();
    }

    @Override
    public synchronized void writeMemory(long address, byte[] data) throws IOException {
        ensureOpen();
        Objects.requireNonNull(data, "data");
        requireMemoryRange(address, data.length);
        if (data.length == 0) {
            return;
        }

        long currentAddress = address;
        int offset = 0;
        while (offset < data.length) {
            int chunkSize = Math.min(data.length - offset, CitraPacketCodec.MAX_WRITE_CONTENT_SIZE);
            byte[] chunk = Arrays.copyOfRange(data, offset, offset + chunkSize);
            long requestId = nextRequestId();
            byte[] request = CitraPacketCodec.writeMemoryRequest(requestId, currentAddress, chunk);
            send(request);
            awaitResponse(requestId, CitraRequestType.WRITE_MEMORY);

            currentAddress += chunkSize;
            offset += chunkSize;
        }
    }

    @Override
    public boolean testConnection() {
        try {
            readMemory(0, 1);
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void close() {
        closed = true;
        socket.close();
    }

    private void send(byte[] request) throws IOException {
        socket.send(new DatagramPacket(request, request.length));
    }

    private CitraPacket awaitResponse(long expectedRequestId, CitraRequestType expectedType) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        byte[] receiveBuffer = new byte[MAX_DATAGRAM_SIZE];

        while (true) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw timeoutException(expectedRequestId);
            }
            socket.setSoTimeout(toTimeoutMillis(remainingNanos));

            DatagramPacket datagram = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                socket.receive(datagram);
            } catch (SocketTimeoutException exception) {
                throw timeoutException(expectedRequestId);
            } catch (SocketException exception) {
                if (closed) {
                    throw new IOException("Citra client is closed", exception);
                }
                throw exception;
            }

            byte[] packetBytes = Arrays.copyOf(datagram.getData(), datagram.getLength());
            CitraPacket packet;
            try {
                packet = CitraPacketCodec.decode(packetBytes);
            } catch (IllegalArgumentException malformedPacket) {
                continue;
            }

            if (packet.version() == CitraPacketCodec.VERSION
                    && packet.requestId() == expectedRequestId
                    && packet.requestType() == expectedType) {
                return packet;
            }
        }
    }

    private long nextRequestId() {
        long requestId = nextRequestId;
        nextRequestId = (nextRequestId + 1) & MAX_UNSIGNED_INT;
        return requestId;
    }

    private void ensureOpen() throws IOException {
        if (closed || socket.isClosed()) {
            throw new IOException("Citra client is closed");
        }
    }

    private static Duration requireValidTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return timeout;
    }

    private static void requireMemoryRange(long address, int size) {
        if (address < 0 || address > MAX_UNSIGNED_INT) {
            throw new IllegalArgumentException("address must fit in an unsigned 32-bit integer");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        if (size > 0 && address > MAX_UNSIGNED_INT - (size - 1L)) {
            throw new IllegalArgumentException("memory operation exceeds the unsigned 32-bit address space");
        }
    }

    private static int toTimeoutMillis(long nanoseconds) {
        long milliseconds = Math.max(1, Duration.ofNanos(nanoseconds).toMillis());
        return (int) Math.min(milliseconds, Integer.MAX_VALUE);
    }

    private static SocketTimeoutException timeoutException(long requestId) {
        return new SocketTimeoutException(
                "Timed out waiting for Citra response " + Long.toUnsignedString(requestId));
    }
}
