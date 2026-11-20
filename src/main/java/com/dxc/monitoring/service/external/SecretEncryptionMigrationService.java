package com.dxc.monitoring.service.external;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.ExternalApplicationAccount;
import com.dxc.monitoring.entity.NotificationChannelSecret;
import com.dxc.monitoring.repository.ExternalApplicationAccountRepository;
import com.dxc.monitoring.repository.NotificationChannelSecretRepository;

/** Re-encrypts every persisted secret atomically before background work is enabled. */
@Service
public class SecretEncryptionMigrationService {
    private final ExternalApplicationAccountRepository externalAccounts;
    private final NotificationChannelSecretRepository notificationSecrets;
    private final ExternalSecretEncryptionService encryption;

    public SecretEncryptionMigrationService(ExternalApplicationAccountRepository externalAccounts,
            NotificationChannelSecretRepository notificationSecrets,
            ExternalSecretEncryptionService encryption) {
        this.externalAccounts = externalAccounts;
        this.notificationSecrets = notificationSecrets;
        this.encryption = encryption;
    }

    @Transactional
    public RotationResult rotate(String previousKey, String currentKey) {
        if (previousKey == null || previousKey.isBlank() || currentKey == null || currentKey.isBlank())
            throw new IllegalStateException("Secret key rotation requires both previous-key and key to be configured.");
        int externalCount = 0;
        for (ExternalApplicationAccount account : externalAccounts.findAll()) {
            String value = account.getEncryptedSecret();
            if (value != null && !value.isBlank()) {
                try {
                    account.setEncryptedSecret(encryption.encryptWithKey(
                            encryption.decryptWithKey(value, previousKey), currentKey));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Secret key rotation failed for external application account "
                            + account.getId() + "; no secrets were migrated.", exception);
                }
                externalCount++;
            }
        }
        int notificationCount = 0;
        for (NotificationChannelSecret secret : notificationSecrets.findAll()) {
            String value = secret.getEncryptedValue();
            if (value != null && !value.isBlank()) {
                try {
                    secret.setEncryptedValue(encryption.encryptWithKey(
                            encryption.decryptWithKey(value, previousKey), currentKey));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException("Secret key rotation failed for notification channel "
                            + secret.getId().getChannelId() + "; no secrets were migrated.", exception);
                }
                notificationCount++;
            }
        }
        externalAccounts.saveAll(externalAccounts.findAll());
        notificationSecrets.saveAll(notificationSecrets.findAll());
        return new RotationResult(externalCount, notificationCount);
    }

    public record RotationResult(int externalApplicationSecrets, int notificationChannelSecrets) {}
}
