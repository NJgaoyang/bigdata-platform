package com.company.platform.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordCipherTest {
    @Test
    void encryptsAndDecryptsWithConfiguredMasterKey() {
        PasswordCipher cipher = new PasswordCipher("0123456789abcdef0123456789abcdef");
        String encrypted = cipher.encrypt("secret-value");
        assertTrue(encrypted.startsWith("ds1:"));
        assertEquals("secret-value", cipher.decrypt(encrypted));
    }

    @Test
    void rejectsMissingMasterKeyAndUnknownCiphertextFormat() {
        PasswordCipher missing = new PasswordCipher("");
        assertThrows(IllegalStateException.class, () -> missing.encrypt("secret-value"));
        PasswordCipher configured = new PasswordCipher("abcdef0123456789abcdef0123456789");
        assertThrows(IllegalStateException.class, () -> configured.decrypt("v3:legacy"));
    }
}
