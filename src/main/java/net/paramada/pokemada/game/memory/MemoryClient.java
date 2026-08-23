package net.paramada.pokemada.game.memory;

import java.io.IOException;

public interface MemoryClient extends AutoCloseable {
    byte[] readMemory(long address, int size) throws IOException;

    void writeMemory(long address, byte[] data) throws IOException;

    boolean testConnection();

    @Override
    void close();
}
