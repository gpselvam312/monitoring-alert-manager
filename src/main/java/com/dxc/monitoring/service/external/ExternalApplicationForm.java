package com.dxc.monitoring.service.external;

public class ExternalApplicationForm
{
    private Long id;
    private String applicationCode;
    private String applicationName;
    private String baseUrl;
    private String authType = "NONE";
    private boolean enabled = true;
    private String accountName = "Shared account";
    private String username;
    private String secret;
    private String secretReference;
    private boolean accountEnabled = true;
    private boolean clearSecret;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getApplicationCode() { return applicationCode; }
    public void setApplicationCode(String applicationCode) { this.applicationCode = applicationCode; }
    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getSecretReference() { return secretReference; }
    public void setSecretReference(String secretReference) { this.secretReference = secretReference; }
    public boolean isAccountEnabled() { return accountEnabled; }
    public void setAccountEnabled(boolean accountEnabled) { this.accountEnabled = accountEnabled; }
    public boolean isClearSecret() { return clearSecret; }
    public void setClearSecret(boolean clearSecret) { this.clearSecret = clearSecret; }
}