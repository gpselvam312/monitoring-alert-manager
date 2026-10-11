# Script and API Execution Security

This document describes the initial fail-closed execution controls for monitoring jobs.

## API method policy

The API executor permits only `GET` and `HEAD`. A missing method defaults to `GET`. All other methods, including `DELETE`, `POST`, `PUT` and `PATCH`, are rejected before a network request is sent. Redirects are disabled so a permitted request does not automatically follow a redirect to another endpoint.

This is a method-level safeguard, not a guarantee that an endpoint is read-only. Configure monitoring endpoints whose GET/HEAD behavior is safe.

## Script configuration

Script execution is denied unless the script:
- resolves to a readable regular file under a configured trusted directory;
- is not itself a symbolic link;
- has a matching SHA-256 entry in the trusted hash configuration;
- does not match the initial dangerous-command or nested-execution patterns; and
- uses a working directory inside the matched trusted script directory, if one is configured.

Configuration is supplied through environment variables:

- `MONITORING_SCRIPT_ALLOWED_DIRECTORIES`: comma-separated absolute trusted directories. Defaults to `${user.dir}/scripts`.
- `MONITORING_SCRIPT_APPROVED_SHA256`: comma-separated entries in the form `/absolute/path/to/script.sh=64-character-sha256`.

Example format (replace the example path and digest with values calculated from your deployed, reviewed script files):

```text
MONITORING_SCRIPT_ALLOWED_DIRECTORIES=/opt/monitoring/scripts
MONITORING_SCRIPT_APPROVED_SHA256=/opt/monitoring/scripts/health.sh=<64-character-sha256>
```

If no approved hash is configured for a script, it will not execute. Update the trusted hash configuration through your normal protected deployment/configuration process whenever an intentional script update is deployed. This is not an in-application approval workflow.

## Important limitations

Static pattern scanning is not a complete shell security boundary. Aliases, variables, obfuscation, interpreter features and helper binaries can evade pattern matching; conservative patterns can also reject harmless comments or commands. Use a dedicated low-privilege operating-system account, filesystem permissions, OS-level sandboxing where possible, and appropriate time/output limits. Apply equivalent restrictions on remote machines if scripts are run over SSH.

The current validator does not make the script filesystem immutable and cannot eliminate all time-of-check/time-of-use races by itself. Keep the script directory and trusted hash configuration writable only by deployment administrators. Test scripts against this policy in a controlled environment before enabling production jobs.
