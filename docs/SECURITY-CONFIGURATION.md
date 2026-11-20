# Runtime security configuration

## Database credentials

Configure `MONITORING_DB_URL`, `MONITORING_DB_USERNAME`, and `MONITORING_DB_PASSWORD` in the deployment secret store. The application YAML intentionally contains no database password. The database credential that was previously committed to source control must be rotated in PostgreSQL and in every deployment before this branch is used outside a disposable development environment.

## Shared secret encryption

All encrypted application secrets use one AES-256-GCM key. Set `MONITORING_ENCRYPTION_KEY` to a Base64-encoded 32-byte key. The legacy `MONITORING_EXTERNAL_APP_ENCRYPTION_KEY` variable remains a temporary fallback for deployments during rollout; migrate deployments to the unified variable.

To rotate the key:

1. Keep the existing key available as `MONITORING_PREVIOUS_ENCRYPTION_KEY`.
2. Set the new key as `MONITORING_ENCRYPTION_KEY`.
3. Set `MONITORING_ENCRYPTION_UPDATE_PWD=true` for exactly one startup.
4. The application re-encrypts both external-application account secrets and notification-channel secrets in one database transaction. Startup fails and rolls back if any stored value cannot be decrypted.
5. After successful startup, set `MONITORING_ENCRYPTION_UPDATE_PWD=false` and remove `MONITORING_PREVIOUS_ENCRYPTION_KEY`.

Scheduled notification/evaluation workers remain idle until the startup migration succeeds. Do not remove the old key before the migration has completed successfully. Back up the database and both keys securely before rotation.

## Script command exceptions

Script validation remains enabled by default. Restricted commands are configured under `monitoring.security.script.validation.restricted-commands`. An exception must identify one restricted command and an exact ordered argument pattern. Wildcards apply only within a single argument; the number of arguments must match. For example:

```yaml
monitoring:
  security:
    script:
      validation:
        allowed-command-rules:
          - command: rm
            allowed-arguments: ["-f", "*.tmp"]
```

This permits only the `rm` invocation with exactly two arguments, where the first is `-f` and the second ends in `.tmp`. It does not permit `rm -rf`, extra arguments, `sudo rm`, nested interpreters, command substitution, or another restricted command. Keep exceptions as narrow as possible.

Static script inspection is defense in depth, not an operating-system sandbox. Run scripts as a dedicated low-privilege account with restricted filesystem and network access.
