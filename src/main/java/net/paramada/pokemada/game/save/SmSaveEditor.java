package net.paramada.pokemada.game.save;

import net.paramada.pokemada.game.official.shared.crypto.PokemonCrypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/** Transactional editor for Pokémon Sun/Moon party data in the persisted save. */
public final class SmSaveEditor {
    public static final int SAVE_SIZE = 0x6BE00;
    private static final int PARTY_OFFSET = 0x1400;
    private static final int PARTY_SIZE = 0x104;
    private static final int PARTY_BLOCK_LENGTH = 0x61C;
    private static final int CHECKSUM_TABLE = SAVE_SIZE - 0x200;
    private static final int NATURE = 0x1C;
    private static final int[] EV_OFFSETS = {0x1E, 0x1F, 0x20, 0x22, 0x23, 0x21};

    public Result modifyNature(Path saveFile, int slot, int expectedSpecies, int nature) throws IOException {
        if (nature < 0 || nature > 24) throw new IllegalArgumentException("Naturaleza inválida");
        return edit(saveFile, slot, expectedSpecies, pokemon -> {
            int before = Byte.toUnsignedInt(pokemon[NATURE]);
            pokemon[NATURE] = (byte) nature;
            return new Change("naturaleza", before, nature);
        });
    }

    public Result addEv(Path saveFile, int slot, int expectedSpecies, String stat, int amount) throws IOException {
        if (amount < 1) throw new IllegalArgumentException("Cantidad de EV inválida");
        int statIndex = switch (stat) {
            case "hp" -> 0; case "attack" -> 1; case "defense" -> 2;
            case "special_attack" -> 3; case "special_defense" -> 4; case "speed" -> 5;
            default -> throw new IllegalArgumentException("Stat de EV inválido: " + stat);
        };
        return edit(saveFile, slot, expectedSpecies, pokemon -> {
            int total = 0;
            for (int offset : EV_OFFSETS) total += Byte.toUnsignedInt(pokemon[offset]);
            int before = Byte.toUnsignedInt(pokemon[EV_OFFSETS[statIndex]]);
            int applied = Math.min(amount, Math.min(252 - before, 510 - total));
            if (applied <= 0) throw new IllegalStateException("El Pokémon ya alcanzó el límite de EV");
            pokemon[EV_OFFSETS[statIndex]] = (byte) (before + applied);
            return new Change(stat, before, before + applied);
        });
    }

    private Result edit(Path path, int slot, int expectedSpecies, Editor editor) throws IOException {
        if (slot < 0 || slot >= 6) throw new IllegalArgumentException("Slot de equipo inválido");
        byte[] original = Files.readAllBytes(path);
        if (original.length != SAVE_SIZE) throw new IllegalArgumentException("El archivo no es un save de Sol/Luna");
        byte[] save = original.clone();
        int offset = PARTY_OFFSET + slot * PARTY_SIZE;
        byte[] decrypted = PokemonCrypto.decrypt(Arrays.copyOfRange(save, offset, offset + PARTY_SIZE));
        if (!PokemonCrypto.hasValidStoredDataChecksum(decrypted)) throw new IllegalStateException("Checksum PK7 inválido");
        int species = u16(decrypted, 0x08);
        if (species != expectedSpecies) throw new IllegalStateException("El equipo del save no coincide con el servidor");
        Change change = editor.apply(decrypted);
        putU16(decrypted, 0x06, PokemonCrypto.calculateStoredDataChecksum(decrypted));
        byte[] encrypted = PokemonCrypto.encrypt(decrypted);
        System.arraycopy(encrypted, 0, save, offset, encrypted.length);
        setPartyBlockChecksum(save);
        SmMemeCrypto.sign(save);
        verify(save, slot, expectedSpecies, change);
        Path backup = path.resolveSibling(path.getFileName() + ".pokemada-" + UUID.randomUUID() + ".bak");
        Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);
        Path temporary = Files.createTempFile(path.getParent(), ".pokemada-save-", ".tmp");
        try {
            Files.write(temporary, save);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
        return new Result(change.label, change.before, change.after, backup);
    }

    private static void verify(byte[] save, int slot, int species, Change expected) {
        int offset = PARTY_OFFSET + slot * PARTY_SIZE;
        byte[] pokemon = PokemonCrypto.decrypt(Arrays.copyOfRange(save, offset, offset + PARTY_SIZE));
        if (!PokemonCrypto.hasValidStoredDataChecksum(pokemon) || u16(pokemon, 0x08) != species)
            throw new IllegalStateException("La verificación posterior del PK7 falló");
        int stored = u16(save, CHECKSUM_TABLE + 0x14 + (4 * 8) + 6);
        int calculated = crc16Invert(save, PARTY_OFFSET, PARTY_BLOCK_LENGTH);
        if (stored != calculated) throw new IllegalStateException("La verificación del checksum del save falló");
    }

    static void setPartyBlockChecksum(byte[] save) {
        putU16(save, CHECKSUM_TABLE + 0x14 + (4 * 8) + 6,
                crc16Invert(save, PARTY_OFFSET, PARTY_BLOCK_LENGTH));
    }

    static int crc16Invert(byte[] data, int offset, int length) {
        int crc = 0xffff;
        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xff;
            for (int bit = 0; bit < 8; bit++) crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0x8408 : crc >>> 1;
        }
        return (~crc) & 0xffff;
    }

    private static int u16(byte[] data, int offset) { return Short.toUnsignedInt(ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort()); }
    private static void putU16(byte[] data, int offset, int value) { ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value); }
    private interface Editor { Change apply(byte[] pokemon); }
    private record Change(String label, int before, int after) { }
    public record Result(String field, int before, int after, Path backup) { }
}
