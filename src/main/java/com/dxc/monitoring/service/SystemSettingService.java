package com.dxc.monitoring.service;

import com.dxc.monitoring.entity.SystemSetting;
import com.dxc.monitoring.entity.User;
import com.dxc.monitoring.repository.SystemSettingRepository;
import com.dxc.monitoring.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    public static final String SCRIPT_ALLOWED_DIRECTORIES = "SCRIPT_ALLOWED_DIRECTORIES";

    private static final Set<String> ALLOWED_THEMES = Set.of(
            "orange", "orange-dark", "blue", "blue-dark", "green", "purple");

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private volatile String cachedApplicationName;
    private volatile String cachedTheme;
    private volatile Boolean cachedSiteOffline;
    private volatile String cachedMaintenanceBypassIps;

    public SystemSettingService(SystemSettingRepository systemSettingRepository, UserRepository userRepository,
            ObjectMapper objectMapper)
    {
        this.systemSettingRepository = systemSettingRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll()
    {
        return systemSettingRepository.findAllByOrderBySortOrderAscSettingKeyAsc();
    }

    @Transactional(readOnly = true)
    public SystemSetting findByKey(String key)
    {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown system setting: " + key));
    }

    @Transactional(readOnly = true)
    public String getValue(String key)
    {
        if (APPLICATION_NAME.equals(key))
            return getApplicationName();
        if (THEME.equals(key))
            return getTheme();
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
                    cachedApplicationName = value = loadValue(APPLICATION_NAME);
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
        String value = cachedTheme;
        if (value == null)
        {
            synchronized (this)
            {
                value = cachedTheme;
                if (value == null)
                {
                    value = loadValue(THEME);
                    value = ALLOWED_THEMES.contains(value) ? value : "orange";
                    cachedTheme = value;
                }
            }
        }
        return value;
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
                    cachedSiteOffline = value = Boolean.parseBoolean(loadValue(SITE_OFFLINE));
            }
        }
        return value;
    }

    public void refreshSiteOfflineCache()
    {
        cachedSiteOffline = Boolean.parseBoolean(loadValue(SITE_OFFLINE));
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
                    cachedMaintenanceBypassIps = value = loadValue(MAINTENANCE_BYPASS_IPS);
            }
        }
        return value;
    }

    /**
     * An empty database array means use the environment/application property,
     * preserving existing deployment defaults.
     */
    @Transactional(readOnly = true)
    public List<String> getScriptAllowedDirectories()
    {
        try
        {
            List<String> directories = objectMapper.readValue(loadValue(SCRIPT_ALLOWED_DIRECTORIES),
                    new TypeReference<List<String>>() {});
            return directories == null ? List.of() : directories.stream()
                    .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception)
        {
            throw new IllegalStateException("SCRIPT_ALLOWED_DIRECTORIES contains invalid JSON configuration.", exception);
        }
    }

    public void update(String key, String value, String username)
    {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found: " + username));
        update(key, value, currentUser);
    }

    public void update(String key, String value, User currentUser)
    {
        SystemSetting setting = findByKey(key);
        validateValue(setting, value);
        if (Objects.equals(setting.getSettingValue(), value))
            return;

        setting.setSettingValue(value);
        setting.setUpdatedAt(OffsetDateTime.now());
        setting.setUpdatedBy(currentUser);
        systemSettingRepository.save(setting);
        refreshCachedSetting(key, value);
    }

    public void updateSettings(Map<String, String> values, Long userId)
    {
        if (values == null || values.isEmpty())
            throw new IllegalArgumentException("No settings were submitted.");

        User currentUser = userRepository.getReferenceById(userId);
        Map<String, SystemSetting> settings = systemSettingRepository.findAllByOrderBySortOrderAscSettingKeyAsc()
                .stream().collect(Collectors.toMap(SystemSetting::getSettingKey, setting -> setting,
                        (first, second) -> first, LinkedHashMap::new));

        for (String key : values.keySet())
        {
            if (!settings.containsKey(key))
                throw new IllegalArgumentException("Unknown system setting: " + key);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<SystemSetting> changed = new ArrayList<>();
        Map<String, String> changedValues = new LinkedHashMap<>();

        values.forEach((key, value) ->
        {
            SystemSetting setting = settings.get(key);
            validateValue(setting, value);
            if (!Objects.equals(setting.getSettingValue(), value))
            {
                setting.setSettingValue(value);
                setting.setUpdatedAt(now);
                setting.setUpdatedBy(currentUser);
                changed.add(setting);
                changedValues.put(key, value);
            }
        });

        if (!changed.isEmpty())
        {
            systemSettingRepository.saveAll(changed);
            changedValues.forEach(this::refreshCachedSetting);
        }
    }

    private void refreshCachedSetting(String key, String value)
    {
        switch (key)
        {
            case APPLICATION_NAME -> cachedApplicationName = value;
            case THEME -> cachedTheme = ALLOWED_THEMES.contains(value) ? value : "orange";
            case SITE_OFFLINE -> cachedSiteOffline = Boolean.parseBoolean(value);
            case MAINTENANCE_BYPASS_IPS -> cachedMaintenanceBypassIps = value;
            default -> { }
        }
    }

    private String loadValue(String key)
    {
        return systemSettingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("System setting not found: " + key))
                .getSettingValue();
    }

    private void validateValue(SystemSetting setting, String value)
    {
        if (value == null)
            throw new IllegalArgumentException("Value cannot be null for setting: " + setting.getSettingKey());

        switch (setting.getDataType())
        {
            case "BOOLEAN" ->
            {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                    throw new IllegalArgumentException("Invalid boolean value for setting: " + setting.getSettingKey());
            }
            case "INTEGER" ->
            {
                try { Integer.parseInt(value); }
                catch (NumberFormatException exception)
                {
                    throw new IllegalArgumentException("Invalid integer value for setting: " + setting.getSettingKey());
                }
            }
            case "JSON" -> validateJson(value, setting.getSettingKey());
            case "STRING" -> { }
            default -> throw new IllegalArgumentException("Unsupported setting data type: " + setting.getDataType());
        }

        if (APPLICATION_NAME.equals(setting.getSettingKey()) && value.isBlank())
            throw new IllegalArgumentException("Application name cannot be blank.");

        if (THEME.equals(setting.getSettingKey()) && !ALLOWED_THEMES.contains(value))
            throw new IllegalArgumentException("Invalid theme. Allowed values: " + String.join(", ", ALLOWED_THEMES));

        if (SCRIPT_ALLOWED_DIRECTORIES.equals(setting.getSettingKey()))
        {
            try
            {
                List<String> directories = objectMapper.readValue(value, new TypeReference<List<String>>() {});
                if (directories == null || directories.stream().anyMatch(path -> path == null || path.isBlank()))
                    throw new IllegalArgumentException("Script allowed directories must be a JSON array of non-empty path strings.");
            }
            catch (com.fasterxml.jackson.core.JsonProcessingException exception)
            {
                throw new IllegalArgumentException("Script allowed directories must be a JSON array of path strings.");
            }
        }
    }

    private void validateJson(String value, String key)
    {
        try
        {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || (!node.isArray() && !node.isObject()))
                throw new IllegalArgumentException("JSON setting must be an array or object: " + key);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception)
        {
            throw new IllegalArgumentException("Invalid JSON value for setting: " + key);
        }
    }
}
