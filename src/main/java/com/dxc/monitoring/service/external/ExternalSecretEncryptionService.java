package com.dxc.monitoring.service.external;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Shared AES-256-GCM encryption for every persisted application secret. */
@Service
public class ExternalSecretEncryptionService {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExternalSecretEncryptionService(
            @Value("${monitoring.security.encryption.key:}") String encodedKey) {
        keyBytes = decodeKey(encodedKey, "monitoring.security.encryption.key");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        return encrypt(plaintext, requireCurrentKey());
    }

    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        return decrypt(encoded, requireCurrentKey());
    }

    /** Used only by the transactional startup key-rotation process. */
    public String encryptWithKey(String plaintext, String encodedKey) {
        if (plaintext == null || plaintext.isEmpty()) return null;
        return encrypt(plaintext, requireKey(decodeKey(encodedKey, "encryption key")));
    }

    /** Used only by the transactional startup key-rotation process. */
    public String decryptWithKey(String encoded, String encodedKey) {
        if (encoded == null || encoded.isBlank()) return null;
        return decrypt(encoded, requireKey(decodeKey(encodedKey, "previous encryption key")));
    }

    private String encrypt(String plaintext, byte[] key) {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt a stored secret.", exception);
        }
    }

    private String decrypt(String encoded, byte[] key) {
        byte[] packed;
        try {
            packed = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored secret has an invalid encoding.", exception);
        }
        if (packed.length <= NONCE_LENGTH)
            throw new IllegalStateException("Stored secret is incomplete.");
        byte[] nonce = Arrays.copyOfRange(packed, 0, NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(packed, NONCE_LENGTH, packed.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to decrypt a stored secret. Verify the configured encryption key.", exception);
        }
    }

    private byte[] requireCurrentKey() {
        return requireKey(keyBytes);
    }

    private byte[] requireKey(byte[] key) {
        if (key == null)
            throw new IllegalStateException("Secret encryption is not configured. Set MONITORING_ENCRYPTION_KEY to a Base64-encoded 32-byte key.");
        return key;
    }

    private static byte[] decodeKey(String encoded, String propertyName) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded.trim());
            if (decoded.length != 32)
                throw new IllegalArgumentException(propertyName + " must be a Base64-encoded 32-byte key.");
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(propertyName + " must be valid Base64 for exactly 32 bytes.", exception);
        }
    }
}
