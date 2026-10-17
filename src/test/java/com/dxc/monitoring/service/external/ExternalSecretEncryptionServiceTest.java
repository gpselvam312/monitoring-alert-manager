package com.dxc.monitoring.service.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class ExternalSecretEncryptionServiceTest
{
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecryptsSecret()
    {
        ExternalSecretEncryptionService service = new ExternalSecretEncryptionService(KEY);
        String encrypted = service.encrypt("sensitive-token");

        assertNotEquals("sensitive-token", encrypted);
        assertEquals("sensitive-token", service.decrypt(encrypted));
    }

    @Test
    void usesDifferentNonceForEachEncryption()
    {
        ExternalSecretEncryptionService service = new ExternalSecretEncryptionService(KEY);

        assertNotEquals(service.encrypt("same-secret"), service.encrypt("same-secret"));
    }

    @Test
    void rejectsTamperedCiphertext()
    {
        ExternalSecretEncryptionService service = new ExternalSecretEncryptionService(KEY);
        String encrypted = service.encrypt("sensitive-token");
        byte[] packed = Base64.getDecoder().decode(encrypted);
        packed[packed.length - 1] ^= 1;

        assertThrows(IllegalStateException.class,
                () -> service.decrypt(Base64.getEncoder().encodeToString(packed)));
    }

    @Test
    void requiresConfiguredKeyWhenEncrypting()
    {
        ExternalSecretEncryptionService service = new ExternalSecretEncryptionService("");

        assertThrows(IllegalStateException.class, () -> service.encrypt("sensitive-token"));
    }

    @Test
    void rejectsKeyWithIncorrectLength()
    {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalArgumentException.class, () -> new ExternalSecretEncryptionService(shortKey));
    }
}
