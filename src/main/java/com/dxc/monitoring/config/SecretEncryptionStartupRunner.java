package com.dxc.monitoring.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.service.external.SecretEncryptionMigrationService;

@Component
public class SecretEncryptionStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SecretEncryptionStartupRunner.class);
    private final SecretEncryptionMigrationService migrationService;
    private final BackgroundWorkReadiness readiness;
    private final String previousKey;
    private final String currentKey;
    private final boolean updatePasswords;

    public SecretEncryptionStartupRunner(SecretEncryptionMigrationService migrationService,
            BackgroundWorkReadiness readiness,
            @Value("${monitoring.security.encryption.previous-key:}") String previousKey,
            @Value("${monitoring.security.encryption.key:}") String currentKey,
            @Value("${monitoring.security.encryption.updatePwd:false}") boolean updatePasswords) {
        this.migrationService = migrationService;
        this.readiness = readiness;
        this.previousKey = previousKey;
        this.currentKey = currentKey;
        this.updatePasswords = updatePasswords;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (updatePasswords) {
            if (currentKey == null || currentKey.isBlank())
                throw new IllegalStateException("MONITORING_ENCRYPTION_KEY is required when updatePwd is true.");
            var result = migrationService.rotate(previousKey, currentKey);
            log.info("Secret encryption key rotation completed: {} external application secrets and {} notification channel secrets.",
                    result.externalApplicationSecrets(), result.notificationChannelSecrets());
        }
        readiness.markReady();
    }
}
