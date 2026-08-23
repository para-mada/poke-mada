package net.paramada.pokemada.protocol.citra;

import java.util.Arrays;
import java.util.Objects;

public record CitraPacket(int version, long requestId, CitraRequestType requestType, byte[] data) {
    public CitraPacket {
        requestType = Objects.requireNonNull(requestType, "requestType");
        data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CitraPacket packet
                && version == packet.version
                && requestId == packet.requestId
                && requestType == packet.requestType
                && Arrays.equals(data, packet.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(version, requestId, requestType);
        return 31 * result + Arrays.hashCode(data);
    }
}
