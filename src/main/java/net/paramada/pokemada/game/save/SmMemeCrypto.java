package net.paramada.pokemada.game.save;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

/** Minimal Pokémon Sun/Moon save signer, ported from PKHeX's documented MemeCrypto flow. */
final class SmMemeCrypto {
    private static final int SIGNATURE_BLOCK = 0x6BA00 + 0x100;
    private static final int CHECKSUM_TABLE = 0x6BE00 - 0x200;
    private static final int SIGNATURE_SIZE = 0x80;
    private static final int RSA_SIZE = 0x60;
    private static final byte[] DER = hex(
            "307c300d06092a864886f70d0101010500036b003068026100" +
            "b61e192091f90a8f76a6eaaa9a3ce58c863f39ae253f037816f5975854e07a9a" +
            "456601e7c94c29759fe155c064eddfa111443f81ef1a428cf6cd32f9dac9d48e" +
            "94cfb3f690120e8e6b9111addaf11e7c96208c37c0143ff2bf3d7e831141a973" +
            "0203010001");
    private static final BigInteger MODULUS = new BigInteger(1, Arrays.copyOfRange(DER, 0x18, 0x79));
    private static final BigInteger PRIVATE_EXPONENT = new BigInteger(1, hex(
            "00775455668fff3cba3026c2d0b26b8085895958341157aeb03b6b0495ee5780" +
            "3e2186eb6cb2eb62a71df18a3c9c6579077670961b3a6102dabe5a194ab58c32" +
            "50aed597fc78978a326db1d7b28dcccb2a3e014edbd397ad33b8f28cd525054251"));

    private SmMemeCrypto() { }

    static void clearSignature(byte[] save) {
        if (save.length != 0x6BE00) throw new IllegalArgumentException("El save no es de Pokémon Sol/Luna");
        Arrays.fill(save, SIGNATURE_BLOCK, SIGNATURE_BLOCK + SIGNATURE_SIZE, (byte) 0);
    }

    static void sign(byte[] save) {
        if (save.length != 0x6BE00) throw new IllegalArgumentException("El save no es de Pokémon Sol/Luna");
        byte[] signature = new byte[SIGNATURE_SIZE];
        byte[] checksumRegion = Arrays.copyOfRange(save, CHECKSUM_TABLE, CHECKSUM_TABLE + 0x140);
        byte[] sha256 = digest("SHA-256", checksumRegion);
        System.arraycopy(sha256, 0, signature, 0, sha256.length);
        signMemeData(signature);
        System.arraycopy(signature, 0, save, SIGNATURE_BLOCK, signature.length);
    }

    static void signMemeData(byte[] signature) {
        if (signature.length < RSA_SIZE) throw new IllegalArgumentException("Firma demasiado corta");
        byte[] sha1 = digest("SHA-1", Arrays.copyOf(signature, signature.length - 8));
        System.arraycopy(sha1, 0, signature, signature.length - 8, 8);
        aesEncrypt(signature);
        signature[signature.length - RSA_SIZE] &= 0x7f;
        byte[] rsaInput = Arrays.copyOfRange(signature, signature.length - RSA_SIZE, signature.length);
        byte[] rsa = unsignedFixed(new BigInteger(1, rsaInput).modPow(PRIVATE_EXPONENT, MODULUS), RSA_SIZE);
        System.arraycopy(rsa, 0, signature, signature.length - RSA_SIZE, RSA_SIZE);
    }

    private static void aesEncrypt(byte[] data) {
        try {
            int payloadLength = data.length - RSA_SIZE;
            byte[] keyMaterial = new byte[DER.length + payloadLength];
            System.arraycopy(DER, 0, keyMaterial, 0, DER.length);
            System.arraycopy(data, 0, keyMaterial, DER.length, payloadLength);
            byte[] key = Arrays.copyOf(digest("SHA-1", keyMaterial), 16);
            Cipher aes = Cipher.getInstance("AES/ECB/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] temp = new byte[16];
            for (int offset = payloadLength; offset < data.length; offset += 16) {
                xor(data, offset, temp);
                temp = aes.doFinal(data, offset, 16);
                System.arraycopy(temp, 0, data, offset, 16);
            }
            for (int i = 0; i < 16; i++) temp[i] ^= data[payloadLength + i];
            byte[] subkey = subkey(temp);
            for (int offset = payloadLength; offset < data.length; offset += 16) xor(data, offset, subkey);
            Arrays.fill(temp, (byte) 0);
            for (int offset = data.length - 16; offset >= payloadLength; offset -= 16) {
                byte[] next = Arrays.copyOfRange(data, offset, offset + 16);
                byte[] encrypted = aes.doFinal(data, offset, 16);
                for (int i = 0; i < 16; i++) data[offset + i] = (byte) (encrypted[i] ^ temp[i]);
                temp = next;
            }
        } catch (Exception failure) {
            throw new IllegalStateException("No se pudo firmar el save", failure);
        }
    }

    private static byte[] subkey(byte[] input) {
        byte[] result = new byte[16];
        for (int i = 0; i < 16; i += 2) {
            int b1 = input[i] & 0xff, b2 = input[i + 1] & 0xff;
            result[i] = (byte) ((b1 << 1) + (b2 >>> 7));
            result[i + 1] = (byte) (b2 << 1);
            if (i + 2 < 16) result[i + 1] += (byte) ((input[i + 2] & 0xff) >>> 7);
        }
        if ((input[0] & 0x80) != 0) result[15] ^= (byte) 0x87;
        return result;
    }

    private static void xor(byte[] target, int offset, byte[] value) {
        for (int i = 0; i < 16; i++) target[offset + i] ^= value[i];
    }

    private static byte[] digest(String algorithm, byte[] data) {
        try { return MessageDigest.getInstance(algorithm).digest(data); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static byte[] unsignedFixed(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        int source = raw.length > length ? raw.length - length : 0;
        byte[] result = new byte[length];
        System.arraycopy(raw, source, result, length - (raw.length - source), raw.length - source);
        return result;
    }

    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
}
