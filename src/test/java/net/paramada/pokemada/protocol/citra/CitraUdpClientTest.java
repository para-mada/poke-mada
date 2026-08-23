package net.paramada.pokemada.protocol.citra;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CitraUdpClientTest {
    @Test
    void splitsReadsIntoLegacySizedRequestsAndCombinesResponses() throws Exception {
        try (DatagramSocket server = loopbackServer()) {
            List<Long> addresses = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            FutureTask<Void> serverTask = runServer(() -> {
                for (int index = 0; index < 3; index++) {
                    ReceivedPacket received = receive(server);
                    CitraPacket request = received.packet();
                    ByteBuffer data = littleEndian(request.data());
                    addresses.add(Integer.toUnsignedLong(data.getInt()));
                    int size = data.getInt();
                    sizes.add(size);

                    byte[] responseData = new byte[size];
                    for (int offset = 0; offset < size; offset++) {
                        responseData[offset] = (byte) (addresses.get(index) + offset);
                    }
                    reply(server, received, CitraPacketCodec.encode(
                            request.requestId(), CitraRequestType.READ_MEMORY, responseData));
                }
            });

            byte[] result;
            try (CitraUdpClient client = clientFor(server, Duration.ofSeconds(1))) {
                result = client.readMemory(0x1000, 70);
            }
            serverTask.get();

            assertEquals(List.of(0x1000L, 0x1020L, 0x1040L), addresses);
            assertEquals(List.of(32, 32, 6), sizes);
            byte[] expected = new byte[70];
            for (int index = 0; index < expected.length; index++) {
                expected[index] = (byte) (0x1000 + index);
            }
            assertArrayEquals(expected, result);
        }
    }

    @Test
    void splitsWritesIntoTwentyFourBytePayloads() throws Exception {
        try (DatagramSocket server = loopbackServer()) {
            List<Long> addresses = new ArrayList<>();
            List<Integer> sizes = new ArrayList<>();
            ByteArrayOutputStream writtenData = new ByteArrayOutputStream();
            FutureTask<Void> serverTask = runServer(() -> {
                for (int index = 0; index < 3; index++) {
                    ReceivedPacket received = receive(server);
                    CitraPacket request = received.packet();
                    ByteBuffer data = littleEndian(request.data());
                    addresses.add(Integer.toUnsignedLong(data.getInt()));
                    int size = data.getInt();
                    sizes.add(size);
                    writtenData.write(data.array(), data.position(), size);

                    reply(server, received, CitraPacketCodec.encode(
                            request.requestId(), CitraRequestType.WRITE_MEMORY, new byte[0]));
                }
            });

            byte[] contents = new byte[50];
            for (int index = 0; index < contents.length; index++) {
                contents[index] = (byte) index;
            }
            try (CitraUdpClient client = clientFor(server, Duration.ofSeconds(1))) {
                client.writeMemory(0x2000, contents);
            }
            serverTask.get();

            assertEquals(List.of(0x2000L, 0x2018L, 0x2030L), addresses);
            assertEquals(List.of(24, 24, 2), sizes);
            assertArrayEquals(contents, writtenData.toByteArray());
        }
    }

    @Test
    void timesOutWhenEmulatorDoesNotReply() throws Exception {
        try (DatagramSocket server = loopbackServer();
             CitraUdpClient client = clientFor(server, Duration.ofMillis(50))) {
            assertThrows(SocketTimeoutException.class, () -> client.readMemory(0, 1));
        }
    }

    @Test
    void testConnectionReturnsFalseInsteadOfLeakingTransportFailure() throws Exception {
        try (DatagramSocket server = loopbackServer();
             CitraUdpClient client = clientFor(server, Duration.ofMillis(50))) {
            assertFalse(client.testConnection());
        }
    }

    @Test
    void rejectsOperationsAfterClose() throws Exception {
        CitraUdpClient client;
        try (DatagramSocket server = loopbackServer()) {
            client = clientFor(server, Duration.ofSeconds(1));
            client.close();
        }

        assertThrows(IOException.class, () -> client.readMemory(0, 1));
        assertThrows(IOException.class, () -> client.writeMemory(0, new byte[]{1}));
    }

    private static DatagramSocket loopbackServer() throws IOException {
        return new DatagramSocket(0, InetAddress.getLoopbackAddress());
    }

    private static CitraUdpClient clientFor(DatagramSocket server, Duration timeout) throws IOException {
        return new CitraUdpClient("localhost", server.getLocalPort(), timeout);
    }

    private static FutureTask<Void> runServer(ThrowingRunnable action) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        Thread.startVirtualThread(task);
        return task;
    }

    private static ReceivedPacket receive(DatagramSocket server) throws IOException {
        byte[] buffer = new byte[256];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        server.receive(datagram);
        byte[] packetBytes = java.util.Arrays.copyOf(datagram.getData(), datagram.getLength());
        return new ReceivedPacket(CitraPacketCodec.decode(packetBytes), datagram.getSocketAddress());
    }

    private static void reply(DatagramSocket server, ReceivedPacket request, byte[] response) throws IOException {
        server.send(new DatagramPacket(response, response.length, request.sender()));
    }

    private static ByteBuffer littleEndian(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private record ReceivedPacket(CitraPacket packet, java.net.SocketAddress sender) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
