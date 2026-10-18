package com.dxc.monitoring.service.external;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.ExternalApplication;
import com.dxc.monitoring.entity.ExternalApplicationAccount;
import com.dxc.monitoring.entity.ExternalApplicationType;
import com.dxc.monitoring.repository.ApplicationRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.ExternalApplicationAccountRepository;
import com.dxc.monitoring.repository.ExternalApplicationRepository;
import com.dxc.monitoring.repository.ExternalApplicationTypeRepository;

@Service
public class ExternalApplicationService
{
    private static final Set<String> AUTH_TYPES = Set.of("NONE", "OIDC", "AUTH_PROXY", "BASIC", "CUSTOM");

    private final ExternalApplicationRepository applicationRepository;
    private final ExternalApplicationAccountRepository accountRepository;
    private final ExternalSecretEncryptionService encryptionService;
    private final ApplicationRepository internalApplicationRepository;
    private final EnvironmentRepository environmentRepository;
    private final ExternalApplicationTypeRepository externalApplicationTypeRepository;

    public ExternalApplicationService(ExternalApplicationRepository applicationRepository,
            ExternalApplicationAccountRepository accountRepository,
            ExternalSecretEncryptionService encryptionService,
            ApplicationRepository internalApplicationRepository,
            EnvironmentRepository environmentRepository,
            ExternalApplicationTypeRepository externalApplicationTypeRepository)
    {
        this.applicationRepository = applicationRepository;
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
        this.internalApplicationRepository = internalApplicationRepository;
        this.environmentRepository = environmentRepository;
        this.externalApplicationTypeRepository = externalApplicationTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<ExternalApplicationType> findEnabledTypes()
    {
        return externalApplicationTypeRepository.findByEnabledTrueOrderBySortOrderAscDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ExternalApplication> findAll()
    {
        return applicationRepository.findAllByOrderByApplicationNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ExternalApplication> findEnabled()
    {
        return applicationRepository.findAllEnabledInstancesForDashboard();
    }

    @Transactional(readOnly = true)
    public ExternalApplication findEnabledById(Long id)
    {
        if (id == null)
            throw new IllegalArgumentException("Select an external application.");
        return applicationRepository.findAllEnabledInstancesForDashboard().stream()
                .filter(application -> application.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The selected external application is not available."));
    }

    @Transactional(readOnly = true)
    public Page<ExternalApplication> findPage(String search, Long applicationId, Long environmentId,
            String externalType, Pageable pageable)
    {
        String normalizedType = externalType == null || externalType.isBlank()
                ? null : externalType.trim().toUpperCase(Locale.ROOT);
        if (normalizedType != null && externalApplicationTypeRepository.findByCodeIgnoreCaseAndEnabledTrue(normalizedType).isEmpty())
            normalizedType = null;
        return applicationRepository.search(search == null ? "" : search.trim(),
                applicationId, environmentId, normalizedType, pageable);
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
        form.setExternalType(app.getExternalType());
        form.setApplicationId(app.getApplication() == null ? null : app.getApplication().getId());
        form.setEnvironmentId(app.getEnvironment() == null ? null : app.getEnvironment().getId());
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
        String name = required(form.getApplicationName(), "Display name");
        String externalType = required(form.getExternalType(), "External application type").toUpperCase(Locale.ROOT);
        if (externalApplicationTypeRepository.findByCodeIgnoreCaseAndEnabledTrue(externalType).isEmpty())
            throw new IllegalArgumentException("Select an enabled external product type.");

        if (form.getApplicationId() == null)
            throw new IllegalArgumentException("Select an internal application.");
        if (form.getEnvironmentId() == null)
            throw new IllegalArgumentException("Select an environment.");

        Application internalApplication = internalApplicationRepository.findById(form.getApplicationId())
                .filter(Application::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Select an enabled internal application."));
        Environment environment = environmentRepository.findById(form.getEnvironmentId())
                .filter(Environment::isEnabled)
                .filter(candidate -> candidate.getApplication().isEnabled())
                .orElseThrow(() -> new IllegalArgumentException("Select an enabled environment."));
        if (!environment.getApplication().getId().equals(internalApplication.getId()))
            throw new IllegalArgumentException("The selected environment does not belong to the selected application.");

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
        app.setExternalType(externalType);
        app.setApplication(internalApplication);
        app.setEnvironment(environment);
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
    public void delete(Long id)
    {
        ExternalApplication app = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("External application was not found."));
        // The shared account is removed by the database's ON DELETE CASCADE foreign key.
        applicationRepository.delete(app);
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