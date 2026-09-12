package com.company.platform.datasource;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class PasswordCipher {
    static final String V2_PREFIX = "v2:";
    static final String V3_PREFIX = "v3:";
    private static final byte[] LEGACY_KEY = "bigdata-platform".getBytes(StandardCharsets.UTF_8);
    private static final int IV_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec primaryKey;
    private final boolean masterKeyConfigured;

    public PasswordCipher() {
        this(System.getenv().getOrDefault("DATASOURCE_MASTER_KEY", ""));
    }

    PasswordCipher(String configuredMasterKey) {
        try {
            String configured = configuredMasterKey == null ? "" : configuredMasterKey;
            masterKeyConfigured = !configured.isBlank();
            primaryKey = masterKeyConfigured
                    ? new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8)), "AES")
                    : legacyKey();
        } catch (Exception ex) {
            throw new IllegalStateException("密码主密钥初始化失败", ex);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            return (masterKeyConfigured ? V3_PREFIX : V2_PREFIX) + encryptGcm(value, primaryKey);
        } catch (Exception ex) {
            throw new IllegalStateException("密码加密失败", ex);
        }
    }

    public String decrypt(String value) {
        try {
            if (value == null || value.isBlank()) return "";
            if (value.startsWith(V3_PREFIX)) {
                if (!masterKeyConfigured) throw new IllegalStateException("当前环境未配置凭据主密钥");
                return decryptGcm(value.substring(V3_PREFIX.length()), primaryKey);
            }
            if (value.startsWith(V2_PREFIX)) {
                String payload = value.substring(V2_PREFIX.length());
                try {
                    return decryptGcm(payload, primaryKey);
                } catch (Exception primaryFailure) {
                    if (!masterKeyConfigured) throw primaryFailure;
                    return decryptGcm(payload, legacyKey());
                }
            }
            return decryptLegacyEcb(value);
        } catch (Exception ex) {
            throw new IllegalStateException("密码解密失败", ex);
        }
    }

    public boolean masterKeyConfigured() { return masterKeyConfigured; }
    public boolean needsRotation(String value) {
        return masterKeyConfigured && value != null && !value.isBlank() && !value.startsWith(V3_PREFIX);
    }
    public String rotate(String value) { return needsRotation(value) ? encrypt(decrypt(value)) : value; }

    private String encryptGcm(String value, SecretKeySpec key) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(payload);
    }

    private String decryptGcm(String payloadText, SecretKeySpec key) throws Exception {
        byte[] payload = Base64.getDecoder().decode(payloadText);
        if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("密码密文格式不合法");
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private String decryptLegacyEcb(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, legacyKey());
        return new String(cipher.doFinal(Base64.getDecoder().decode(value)), StandardCharsets.UTF_8);
    }
    private SecretKeySpec legacyKey() { return new SecretKeySpec(LEGACY_KEY, "AES"); }
}
