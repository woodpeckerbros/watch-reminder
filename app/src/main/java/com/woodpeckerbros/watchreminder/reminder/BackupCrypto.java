package com.woodpeckerbros.watchreminder.reminder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Shared watch/phone backup envelope: AES-GCM confidentiality plus tamper detection. */
public final class BackupCrypto {
    private static final byte[] MAGIC = new byte[]{'Z', 'M', 'B', 'U'};
    private static final byte VERSION = 3;
    private static final int IV_BYTES = 12;
    private static final String TEXT_PREFIX = "ZMBU3:";
    // Both installed artifacts need the same key to exchange backups without a server/passphrase.
    // This protects files from casual inspection and unauthenticated editing, not a reverse engineer.
    private static final byte[] KEY = Base64.getDecoder().decode("VyTK4ZgW4m3t7lvb5KTRuEOcNb2qeD2rhIYIe2UT1OM=");

    private BackupCrypto() { }

    public static byte[] encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        byte[] header = header(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(header);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream(header.length + encrypted.length);
        output.write(header);
        output.write(encrypted);
        return output.toByteArray();
    }

    public static String decryptToText(byte[] data) throws Exception {
        if (!isEncrypted(data)) return new String(data, StandardCharsets.UTF_8); // legacy only
        byte[] iv = Arrays.copyOfRange(data, MAGIC.length + 2, MAGIC.length + 2 + IV_BYTES);
        byte[] header = Arrays.copyOfRange(data, 0, MAGIC.length + 2 + IV_BYTES);
        byte[] encrypted = Arrays.copyOfRange(data, header.length, data.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(header);
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    public static String encodeForTextTransport(byte[] data) {
        return TEXT_PREFIX + Base64.getEncoder().encodeToString(data);
    }

    public static byte[] decodeTextTransport(String text) throws Exception {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.startsWith(TEXT_PREFIX)) return trimmed.getBytes(StandardCharsets.UTF_8);
        return Base64.getDecoder().decode(trimmed.substring(TEXT_PREFIX.length()));
    }

    public static boolean isEncrypted(byte[] data) {
        return data != null && data.length > MAGIC.length + 2 + IV_BYTES + 16
                && data[0] == MAGIC[0] && data[1] == MAGIC[1]
                && data[2] == MAGIC[2] && data[3] == MAGIC[3] && data[4] == VERSION
                && data[5] == IV_BYTES;
    }

    private static byte[] header(byte[] iv) {
        ByteArrayOutputStream output = new ByteArrayOutputStream(MAGIC.length + 2 + IV_BYTES);
        output.write(MAGIC, 0, MAGIC.length);
        output.write(VERSION);
        output.write(IV_BYTES);
        output.write(iv, 0, iv.length);
        return output.toByteArray();
    }
}
