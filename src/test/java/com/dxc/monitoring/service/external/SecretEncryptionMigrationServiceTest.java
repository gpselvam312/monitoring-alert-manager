package com.dxc.monitoring.service.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.ExternalApplicationAccount;
import com.dxc.monitoring.entity.NotificationChannelSecret;
import com.dxc.monitoring.entity.NotificationChannelSecretId;
import com.dxc.monitoring.repository.ExternalApplicationAccountRepository;
import com.dxc.monitoring.repository.NotificationChannelSecretRepository;

class SecretEncryptionMigrationServiceTest {
    private static final String OLD_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String NEW_KEY = newKey();

    @Test
    void rotatesBothSecretStores() {
        ExternalApplicationAccountRepository external = mock(ExternalApplicationAccountRepository.class);
        NotificationChannelSecretRepository notifications = mock(NotificationChannelSecretRepository.class);
        ExternalSecretEncryptionService crypto = new ExternalSecretEncryptionService(NEW_KEY);
        ExternalApplicationAccount account = new ExternalApplicationAccount();
        account.setId(42L);
        account.setEncryptedSecret(crypto.encryptWithKey("external-token", OLD_KEY));
        NotificationChannelSecret secret = new NotificationChannelSecret();
        secret.setId(new NotificationChannelSecretId(9L, "webhook-url"));
        secret.setEncryptedValue(crypto.encryptWithKey("notification-token", OLD_KEY));
        when(external.findAll()).thenReturn(List.of(account));
        when(notifications.findAll()).thenReturn(List.of(secret));

        var result = new SecretEncryptionMigrationService(external, notifications, crypto).rotate(OLD_KEY, NEW_KEY);

        assertEquals(1, result.externalApplicationSecrets());
        assertEquals(1, result.notificationChannelSecrets());
        assertEquals("external-token", crypto.decrypt(account.getEncryptedSecret()));
        assertEquals("notification-token", crypto.decrypt(secret.getEncryptedValue()));
    }

    @Test
    void failsClosedWhenAnyStoredSecretCannotBeDecrypted() {
        ExternalApplicationAccountRepository external = mock(ExternalApplicationAccountRepository.class);
        NotificationChannelSecretRepository notifications = mock(NotificationChannelSecretRepository.class);
        ExternalApplicationAccount account = new ExternalApplicationAccount();
        account.setId(42L);
        account.setEncryptedSecret("not-valid-ciphertext");
        when(external.findAll()).thenReturn(List.of(account));
        when(notifications.findAll()).thenReturn(List.of());
        var service = new SecretEncryptionMigrationService(external, notifications,
                new ExternalSecretEncryptionService(NEW_KEY));

        assertThrows(IllegalStateException.class, () -> service.rotate(OLD_KEY, NEW_KEY));
    }

    private static String newKey() {
        byte[] bytes = new byte[32];
        bytes[0] = 1;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
