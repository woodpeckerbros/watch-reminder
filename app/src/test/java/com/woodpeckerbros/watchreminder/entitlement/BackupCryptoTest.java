package com.woodpeckerbros.watchreminder.entitlement;

import com.woodpeckerbros.watchreminder.reminder.BackupCrypto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupCryptoTest {
    @Test public void encryptedBackupIsNotPlaintextAndRoundTrips() throws Exception {
        String json = "{\"trialStartedAt\":1700000000000,\"reminders\":[]}";
        byte[] encrypted = BackupCrypto.encrypt(json);
        assertTrue(BackupCrypto.isEncrypted(encrypted));
        assertFalse(new String(encrypted, java.nio.charset.StandardCharsets.ISO_8859_1).contains("reminders"));
        assertEquals(json, BackupCrypto.decryptToText(encrypted));
    }

    @Test(expected = Exception.class) public void editingEncryptedBackupBreaksAuthentication() throws Exception {
        byte[] encrypted = BackupCrypto.encrypt("{\"x\":1}");
        encrypted[encrypted.length - 1] ^= 0x01;
        BackupCrypto.decryptToText(encrypted);
    }
}
