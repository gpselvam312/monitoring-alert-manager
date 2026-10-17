package com.dxc.monitoring.service.external;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExternalSecretEncryptionService
{
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final byte[] keyBytes;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExternalSecretEncryptionService(
            @Value("${monitoring.external-applications.encryption-key:}") String encodedKey)
    {
        if (encodedKey == null || encodedKey.isBlank())
        {
            keyBytes = null;
        }
        else
        {
            try
            {
                byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
                if (decoded.length != 32)
                {
                    throw new IllegalArgumentException(
                            "MONITORING_EXTERNAL_APP_ENCRYPTION_KEY must be a Base64-encoded 32-byte key.");
                }
                keyBytes = decoded;
            }
            catch (IllegalArgumentException exception)
            {
                throw new IllegalArgumentException(
                        "MONITORING_EXTERNAL_APP_ENCRYPTION_KEY must be valid Base64 for exactly 32 bytes.", exception);
            }
        }
    }

    public String encrypt(String plaintext)
    {
        if (plaintext == null || plaintext.isEmpty())
            return null;
        byte[] key = requireKey();
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        try
        {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        }
        catch (GeneralSecurityException exception)
        {
            throw new IllegalStateException("Unable to encrypt the external application secret.", exception);
        }
    }

    public String decrypt(String encoded)
    {
        if (encoded == null || encoded.isBlank())
            return null;
        byte[] packed;
        try
        {
            packed = Base64.getDecoder().decode(encoded);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalStateException("Stored external application secret has an invalid encoding.", exception);
        }
        if (packed.length <= NONCE_LENGTH)
            throw new IllegalStateException("Stored external application secret is incomplete.");

        byte[] nonce = Arrays.copyOfRange(packed, 0, NONCE_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(packed, NONCE_LENGTH, packed.length);
        try
        {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(requireKey(), "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException exception)
        {
            throw new IllegalStateException(
                    "Unable to decrypt the external application secret. Verify the configured encryption key.", exception);
        }
    }

    private byte[] requireKey()
    {
        if (keyBytes == null)
        {
            throw new IllegalStateException(
                    "External secret encryption is not configured. Set MONITORING_EXTERNAL_APP_ENCRYPTION_KEY to a Base64-encoded 32-byte key.");
        }
        return keyBytes;
    }
}