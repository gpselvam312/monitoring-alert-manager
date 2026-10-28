package com.dxc.monitoring.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.NotificationChannelSecret;
import com.dxc.monitoring.entity.NotificationChannelSecretId;
import com.dxc.monitoring.repository.NotificationChannelSecretRepository;
import com.dxc.monitoring.service.external.ExternalSecretEncryptionService;

@Service
public class NotificationChannelSecretService {
    private final NotificationChannelSecretRepository repository;
    private final ExternalSecretEncryptionService encryptionService;

    public NotificationChannelSecretService(NotificationChannelSecretRepository repository,
            ExternalSecretEncryptionService encryptionService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
    }

    @Transactional
    public void saveSecret(Long channelId, String secretKey, String plaintext, Long userId) {
        if (channelId == null || secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("Notification channel and secret key are required.");
        }
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("Notification secret cannot be blank.");
        }
        NotificationChannelSecretId id = new NotificationChannelSecretId(channelId, secretKey.trim());
        NotificationChannelSecret secret = repository.findById(id).orElseGet(NotificationChannelSecret::new);
        secret.setId(id);
        secret.setEncryptedValue(encryptionService.encrypt(plaintext));
        secret.setUpdatedBy(userId);
        repository.save(secret);
    }

    @Transactional(readOnly = true)
    public String getSecret(Long channelId, String secretKey) {
        if (channelId == null || secretKey == null || secretKey.isBlank()) return null;
        return repository.findById(new NotificationChannelSecretId(channelId, secretKey))
                .map(secret -> encryptionService.decrypt(secret.getEncryptedValue()))
                .orElse(null);
    }
}
