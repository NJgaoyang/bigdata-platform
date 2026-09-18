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
    static final String PREFIX = "ds1:";
    private static final int IV_LENGTH = 12;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec primaryKey;

    public PasswordCipher() {
        this(System.getenv().getOrDefault("DATASPHERE_CREDENTIAL_MASTER_KEY", ""));
    }

    public PasswordCipher(String configuredMasterKey) {
        try {
            String configured = configuredMasterKey == null ? "" : configuredMasterKey.trim();
            primaryKey = configured.isBlank() ? null
                    : new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8)), "AES");        } catch (Exception ex) {
            throw new IllegalStateException("凭据主密钥初始化失败", ex);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return "";
        requireMasterKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, primaryKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("凭据加密失败", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        requireMasterKey();
        if (!value.startsWith(PREFIX)) throw new IllegalStateException("不支持的凭据密文格式");
        try {            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("凭据密文格式不合法");
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, primaryKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("凭据解密失败", ex);
        }
    }

    public boolean masterKeyConfigured() { return primaryKey != null; }

    private void requireMasterKey() {
        if (primaryKey == null) throw new IllegalStateException("未配置 DATASPHERE_CREDENTIAL_MASTER_KEY");
    }
}
