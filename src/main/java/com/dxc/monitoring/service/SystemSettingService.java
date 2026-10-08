package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.SystemSetting;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.SystemSettingRepository;
import com.dxc.monitoring.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class SystemSettingService
{
    public static final String APPLICATION_NAME = "APPLICATION_NAME";
    public static final String THEME = "THEME";
    public static final String SITE_OFFLINE = "SITE_OFFLINE";
    public static final String MAINTENANCE_MESSAGE = "MAINTENANCE_MESSAGE";
    public static final String MAINTENANCE_BYPASS_IPS = "MAINTENANCE_BYPASS_IPS";

    private static final List<String> ALLOWED_THEMES = List.of("orange", "blue", "green", "purple","orange-dark", "blue-dark");

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;

    /*
     * These settings are read on almost every page request. Keep them in memory instead of querying PostgreSQL
     * repeatedly.
     */
    private volatile String cachedApplicationName;
    private volatile String cachedTheme;
    private volatile Boolean cachedSiteOffline;
    private volatile String cachedMaintenanceBypassIps;

    public SystemSettingService(SystemSettingRepository systemSettingRepository, UserRepository userRepository)
    {
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll()
    {
        return systemSettingRepository.findAllByOrderBySettingKeyAsc();
    }

    @Transactional(readOnly = true)
    public SystemSetting findByKey(String key)
    {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("System setting not found: " + key));
    }

    @Transactional(readOnly = true)
    public String getValue(String key)
    {
        if (APPLICATION_NAME.equals(key))
        {
            return getApplicationName();
        }

        if (THEME.equals(key))
        {
            return getTheme();
        }

        return findByKey(key).getSettingValue();
    }

    @Transactional(readOnly = true)
    public String getApplicationName()
    {
        String value = cachedApplicationName;

        if (value == null)
        {
            synchronized (this)
            {
                value = cachedApplicationName;

                if (value == null)
                {
                    value = loadValue(APPLICATION_NAME);
                    cachedApplicationName = value;
                }
            }
        }

        return value;
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key)
    {
        return Boolean.parseBoolean(getValue(key));
    }

    @Transactional(readOnly = true)
    public String getTheme()
    {
        String theme = cachedTheme;

        if (theme == null)
        {
            synchronized (this)
            {
                theme = cachedTheme;

                if (theme == null)
                {
                    theme = loadValue(THEME);

                    if (!ALLOWED_THEMES.contains(theme))
                    {
                        theme = "orange";
                    }

                    cachedTheme = theme;
                }
            }
        }

        return theme;
    }

    @Transactional(readOnly = true)
    public boolean isSiteOffline()
    {
        Boolean value = cachedSiteOffline;

        if (value == null)
        {
            synchronized (this)
            {
                value = cachedSiteOffline;

                if (value == null)
                {
                    value = loadBooleanValue(SITE_OFFLINE);
                    cachedSiteOffline = value;
                }
            }
        }

        return value;
    }
    
    public void refreshSiteOfflineCache() {
        cachedSiteOffline = loadBooleanValue(SITE_OFFLINE);
    }
    
    private boolean loadBooleanValue(String key)
    {
        return Boolean.parseBoolean(loadValue(key));
    }

    @Transactional(readOnly = true)
    public String getMaintenanceBypassIps()
    {
        String value = cachedMaintenanceBypassIps;

        if (value == null)
        {
            synchronized (this)
            {
                value = cachedMaintenanceBypassIps;

                if (value == null)
                {
                    value = loadValue(MAINTENANCE_BYPASS_IPS);
                    cachedMaintenanceBypassIps = value;
                }
            }
        }

        return value;
    }

    
    /*
     * Existing single-setting update method. Keep this for other callers that may need to update one setting.
     */
    public void update(String key, String value, String username)
    {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + username));

        update(key, value, currentUser);
    }

    /*
     * Existing single-setting update method.
     */
    public void update(String key, String value, User currentUser)
    {
        SystemSetting setting = findByKey(key);

        validateValue(setting, value);

        setting.setSettingValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        setting.setUpdatedBy(currentUser);

        systemSettingRepository.save(setting);

        /*
         * Update the in-memory cache immediately after the DB update.
         */
        if (APPLICATION_NAME.equals(key))
        {
            cachedApplicationName = value;
        }

        if (THEME.equals(key))
        {
            cachedTheme = ALLOWED_THEMES.contains(value) ? value : "orange";
        }
    }

    /*
     * Bulk update for the Settings page.
     *
     * Loads the current user once and all settings in one DB query. Only settings whose values actually changed
     * are updated.
     */
    public void updateSettings(String applicationName, String theme, boolean siteOffline, String maintenanceMessage,
            String maintenanceBypassIps, Long userId)
    {
        User currentUser = userRepository.getReferenceById(userId);

        Map<String, SystemSetting> settings = systemSettingRepository.findAllByOrderBySettingKeyAsc().stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, Function.identity()));

        List<SystemSetting> changedSettings = new ArrayList<>();

        OffsetDateTime now = OffsetDateTime.now();

        if (updateIfChanged(settings.get(APPLICATION_NAME), applicationName, currentUser, now))
        {
            changedSettings.add(settings.get(APPLICATION_NAME));
        }

        if (updateIfChanged(settings.get(THEME), theme, currentUser, now))
        {
            changedSettings.add(settings.get(THEME));
        }

        if (updateIfChanged(settings.get(SITE_OFFLINE), Boolean.toString(siteOffline), currentUser, now))
        {
            changedSettings.add(settings.get(SITE_OFFLINE));
        }

        if (updateIfChanged(settings.get(MAINTENANCE_MESSAGE), maintenanceMessage, currentUser, now))
        {
            changedSettings.add(settings.get(MAINTENANCE_MESSAGE));
        }

        if (updateIfChanged(settings.get(MAINTENANCE_BYPASS_IPS), maintenanceBypassIps, currentUser, now))
        {
            changedSettings.add(settings.get(MAINTENANCE_BYPASS_IPS));
        }

        if (!changedSettings.isEmpty())
        {
            systemSettingRepository.saveAll(changedSettings);
        }

        /*
         * Refresh cached values only when they actually changed.
         */
        if (changedSettings.stream().anyMatch(setting -> APPLICATION_NAME.equals(setting.getSettingKey())))
        {
            cachedApplicationName = applicationName;
        }

        if (changedSettings.stream().anyMatch(setting -> THEME.equals(setting.getSettingKey())))
        {
            cachedTheme = ALLOWED_THEMES.contains(theme) ? theme : "orange";
        }

        if (changedSettings.stream().anyMatch(setting -> SITE_OFFLINE.equals(setting.getSettingKey())))
        {
            cachedSiteOffline = siteOffline;
        }

        if (changedSettings.stream().anyMatch(setting -> MAINTENANCE_BYPASS_IPS.equals(setting.getSettingKey())))
        {
            cachedMaintenanceBypassIps = maintenanceBypassIps;
        }
    }

    private boolean updateIfChanged(SystemSetting setting, String newValue, User currentUser, OffsetDateTime now)
    {
        if (setting == null)
        {
            throw new IllegalArgumentException("Required system setting is missing.");
        }

        validateValue(setting, newValue);

        if (Objects.equals(setting.getSettingValue(), newValue))
        {
            return false;
        }

        setting.setSettingValue(newValue);
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(currentUser);

        return true;
    }

    private String loadValue(String key)
    {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("System setting not found: " + key)).getSettingValue();
    }

    private void validateValue(SystemSetting setting, String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Value cannot be null for setting: " + setting.getSettingKey());
        }

        switch (setting.getDataType())
        {
            case "BOOLEAN":
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                {
                    throw new IllegalArgumentException("Invalid boolean value for setting: " + setting.getSettingKey());
                }
                break;

            case "INTEGER":
                try
                {
                    Integer.parseInt(value);
                } catch (NumberFormatException ex)
                {
                    throw new IllegalArgumentException("Invalid integer value for setting: " + setting.getSettingKey());
                }
                break;

            case "JSON":
                validateJson(value, setting.getSettingKey());
                break;

            case "STRING":
                break;

            default:
                throw new IllegalArgumentException("Unsupported setting data type: " + setting.getDataType());
        }

        if (THEME.equals(setting.getSettingKey()) && !ALLOWED_THEMES.contains(value))
        {
            throw new IllegalArgumentException("Invalid theme. Allowed values: " + String.join(", ", ALLOWED_THEMES));
        }
    }

    private void validateJson(String value, String key)
    {
        String trimmed = value.trim();

        if (!((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))))
        {
            throw new IllegalArgumentException("Invalid JSON value for setting: " + key);
        }
    }
}
