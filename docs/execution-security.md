# Script and API Execution Security

This document describes the initial fail-closed execution controls for monitoring jobs.

## API method policy

The API executor permits only `GET` and `HEAD`. A missing method defaults to `GET`. All other methods, including `DELETE`, `POST`, `PUT` and `PATCH`, are rejected before a network request is sent. Redirects are disabled so a permitted request does not automatically follow a redirect to another endpoint.

This is a method-level safeguard, not a guarantee that an endpoint is read-only. Configure monitoring endpoints whose GET/HEAD behavior is safe.

## Script configuration

Script execution is denied unless the script:
- resolves to a readable regular file under a configured trusted directory;
- is not itself a symbolic link;
- has a matching SHA-256 hash stored on that Monitoring Job;
- does not match the initial dangerous-command or nested-execution patterns; and
- uses a working directory inside the matched trusted script directory, if one is configured.

The trusted directories are configured through environment variables:

- `MONITORING_SCRIPT_ALLOWED_DIRECTORIES`: comma-separated absolute trusted directories. Defaults to `${user.dir}/scripts`.

The approved SHA-256 hash is stored in the **Approved SHA-256** field on each Monitoring Job. Users who already have permission to create or edit jobs can maintain the hash; the existing job permissions are unchanged. The hash is checked at execution time, so a hash update does not require an application restart. A missing hash or mismatch blocks execution and is recorded as a specific execution error for the dashboard widget and execution history.

Calculate a hash on the host that stores the script:

```bash
sha256sum /opt/monitoring/scripts/health.sh
```

Copy the 64-character digest into the corresponding job's **Approved SHA-256** field. Update it only when the new script version has been reviewed and is intended to be trusted.

## Important limitations

Static pattern scanning is not a complete shell security boundary. Aliases, variables, obfuscation, interpreter features and helper binaries can evade pattern matching; conservative patterns can also reject harmless comments or commands. Use a dedicated low-privilege operating-system account, filesystem permissions, OS-level sandboxing where possible, and appropriate time/output limits. Apply equivalent restrictions on remote machines if scripts are run over SSH.

The current validator does not make the script filesystem immutable and cannot eliminate all time-of-check/time-of-use races by itself. Keep the script directory and trusted hash configuration writable only by deployment administrators. Test scripts against this policy in a controlled environment before enabling production jobs.
