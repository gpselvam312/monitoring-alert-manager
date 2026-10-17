# External applications and iFrame widgets

## Configure the encryption key

The shared external-account secret is encrypted with AES-256-GCM before it is stored in PostgreSQL. The encryption key must be supplied outside the repository and database.

Generate a key for a deployment using a secure secret-management process, for example:

```bash
openssl rand -base64 32
```

Set the resulting Base64 value as `MONITORING_EXTERNAL_APP_ENCRYPTION_KEY` in the application runtime environment or secret manager. Do not commit the value, put it in `application.yml`, or store it in PostgreSQL. Every application instance that must decrypt these secrets needs the same key. Back it up securely: losing it makes encrypted secrets unrecoverable; rotating it requires a planned re-encryption process.

The application can start without the key if no encrypted secret is being saved. Saving a secret in the administration screen fails until the key is configured. A secret-manager reference can be stored without this key, but no secret-manager resolver is implemented yet.

## Configure an external application

Open **Administration → External Applications** and configure a unique application code, display name, base URL, authentication type, and optional shared account metadata. One shared account row is allowed per external application. The internal `ra_fcb.users` table and its RBAC model are not changed.

The secret field is write-only. It is never populated from the database or returned by the administration controller. Leave it blank to retain an existing encrypted secret; use the clear-secret checkbox to remove the stored secret/reference.

## Authentication and browser framing limitations

The authentication type is configuration metadata, not a browser login implementation. Credentials stored here do not create a session in an embedded browser page. OIDC/SSO or an authentication proxy must be configured and supported by the external application. Basic/custom authentication require a separately designed integration; credentials must not be added to the iFrame URL or automatically typed into a login form.

The external site must allow embedding through its CSP `frame-ancestors` policy / `X-Frame-Options`, and its cookie policy must permit the intended browser context. Some sites intentionally disallow third-party framing. No application-side workaround should bypass those controls.
