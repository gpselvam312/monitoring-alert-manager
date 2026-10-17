package com.dxc.monitoring.service.external;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.ExternalApplication;
import com.dxc.monitoring.entity.ExternalApplicationAccount;
import com.dxc.monitoring.repository.ExternalApplicationAccountRepository;
import com.dxc.monitoring.repository.ExternalApplicationRepository;

@Service
public class ExternalApplicationService
{
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "OIDC", "AUTH_PROXY", "BASIC", "CUSTOM");

    private final ExternalApplicationRepository applicationRepository;
    private final ExternalApplicationAccountRepository accountRepository;
    private final ExternalSecretEncryptionService encryptionService;

    public ExternalApplicationService(ExternalApplicationRepository applicationRepository,
            ExternalApplicationAccountRepository accountRepository,
            ExternalSecretEncryptionService encryptionService)
    {
        this.applicationRepository = applicationRepository;
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
    }

    @Transactional(readOnly = true)
    public List<ExternalApplication> findAll()
    {
        return applicationRepository.findAllByOrderByApplicationNameAsc();
    }

    @Transactional(readOnly = true)
    public ExternalApplicationForm getForm(Long id)
    {
        ExternalApplicationForm form = new ExternalApplicationForm();
        if (id == null)
            return form;

        ExternalApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("External application was not found."));
        form.setId(app.getId());
        form.setApplicationCode(app.getApplicationCode());
        form.setApplicationName(app.getApplicationName());
        form.setBaseUrl(app.getBaseUrl());
        form.setAuthType(app.getAuthType());
        form.setEnabled(app.isEnabled());

        accountRepository.findByExternalApplication_Id(id).ifPresent(account -> {
            form.setAccountName(account.getAccountName());
            form.setUsername(account.getUsername());
            form.setSecretReference(account.getSecretReference());
            form.setAccountEnabled(account.isEnabled());
            // The stored secret is deliberately never copied into the form.
        });
        return form;
    }

    @Transactional
    public void save(ExternalApplicationForm form)
    {
        String code = required(form.getApplicationCode(), "Application code").toUpperCase(Locale.ROOT);
        String name = required(form.getApplicationName(), "Application name");
        String url = validateBaseUrl(form.getBaseUrl());
        String authType = required(form.getAuthType(), "Authentication type").toUpperCase(Locale.ROOT);
        if (!AUTH_TYPES.contains(authType))
            throw new IllegalArgumentException("Select a supported authentication type.");

        if (applicationRepository.findByApplicationCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(form.getId())).isPresent())
            throw new IllegalArgumentException("An external application with this code already exists.");

        ExternalApplication app = form.getId() == null ? new ExternalApplication()
                : applicationRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException("External application was not found."));
        app.setApplicationCode(code);
        app.setApplicationName(name);
        app.setBaseUrl(url);
        app.setAuthType(authType);
        app.setEnabled(form.isEnabled());
        ExternalApplication savedApp = applicationRepository.save(app);

        String secretReference = blankToNull(form.getSecretReference());
        String secret = form.getSecret();
        if (secret != null && !secret.isBlank() && secretReference != null)
            throw new IllegalArgumentException("Use either an encrypted secret or a secret reference, not both.");

        ExternalApplicationAccount account = accountRepository.findByExternalApplication_Id(savedApp.getId())
                .orElseGet(() -> {
                    ExternalApplicationAccount created = new ExternalApplicationAccount();
                    created.setExternalApplication(savedApp);
                    return created;
                });

        boolean hasAccountData = !blank(form.getAccountName()) || !blank(form.getUsername())
                || !blank(secret) || secretReference != null || form.isClearSecret();
        if (hasAccountData || account.getId() != null)
        {
            account.setExternalApplication(savedApp);
            account.setAccountName(blank(form.getAccountName()) ? "Shared account" : form.getAccountName().trim());
            account.setUsername(blankToNull(form.getUsername()));
            account.setEnabled(form.isAccountEnabled());

            if (form.isClearSecret())
            {
                account.setEncryptedSecret(null);
                account.setSecretReference(null);
            }
            else if (secretReference != null)
            {
                account.setSecretReference(secretReference);
                account.setEncryptedSecret(null);
            }
            else if (secret != null && !secret.isBlank())
            {
                account.setEncryptedSecret(encryptionService.encrypt(secret));
                account.setSecretReference(null);
            }
            accountRepository.save(account);
        }
    }

    @Transactional
    public void toggleEnabled(Long id)
    {
        ExternalApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("External application was not found."));
        app.setEnabled(!app.isEnabled());
    }

    @Transactional(readOnly = true)
    public boolean hasAccount(Long id)
    {
        return accountRepository.findByExternalApplication_Id(id)
                .map(account -> account.getUsername() != null || account.getEncryptedSecret() != null
                        || account.getSecretReference() != null)
                .orElse(false);
    }

    private String validateBaseUrl(String value)
    {
        String url = required(value, "Base URL");
        try
        {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
        }
        catch (RuntimeException exception)
        {
            throw new IllegalArgumentException(
                    "Base URL must be an absolute HTTP or HTTPS URL without embedded credentials or a fragment.");
        }
        return url;
    }

    private String required(String value, String label)
    {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " is required.");
        return value.trim();
    }

    private boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value)
    {
        return blank(value) ? null : value.trim();
    }
}