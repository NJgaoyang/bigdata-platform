package com.company.platform.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordCipherTest {
    @Test
    void masterKeyCanReadAndRotateExistingV2Ciphertext() {
        PasswordCipher legacy = new PasswordCipher("");
        String existing = legacy.encrypt("secret-value");
        assertTrue(existing.startsWith("v2:"));

        PasswordCipher secured = new PasswordCipher("0123456789abcdef0123456789abcdef");
        assertEquals("secret-value", secured.decrypt(existing));
        assertTrue(secured.needsRotation(existing));

        String rotated = secured.rotate(existing);
        assertTrue(rotated.startsWith("v3:"));
        assertEquals("secret-value", secured.decrypt(rotated));
        assertFalse(secured.needsRotation(rotated));
    }

    @Test
    void v3CiphertextRequiresConfiguredMasterKey() {
        PasswordCipher secured = new PasswordCipher("abcdef0123456789abcdef0123456789");
        String encrypted = secured.encrypt("secret-value");
        assertTrue(encrypted.startsWith("v3:"));
        assertThrows(IllegalStateException.class, () -> new PasswordCipher("").decrypt(encrypted));
    }
}
