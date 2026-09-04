package com.company.platform.datasource;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordCipher {
    private static final String PREFIX = "v2:";
    private static final byte[] LEGACY_KEY = "bigdata-platform".getBytes(StandardCharsets.UTF_8);
    private static final int IV_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("密码加密失败", ex);
        }
    }

    public String decrypt(String value) {
        try {
            if (value == null || value.isBlank()) return "";
            if (!value.startsWith(PREFIX)) return decryptLegacy(value);
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("密码解密失败", ex);
        }
    }
    private String decryptLegacy(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(LEGACY_KEY, "AES"));
        return new String(cipher.doFinal(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
    }
    private SecretKeySpec key() throws Exception {
        String configured = System.getenv().getOrDefault("DATASOURCE_MASTER_KEY", "");
        // Keep unit tests and the offline development profile usable. Production
        // deployments must provide DATASOURCE_MASTER_KEY to activate GCM keys.
        if (configured.isBlank()) return new SecretKeySpec(LEGACY_KEY, "AES");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
