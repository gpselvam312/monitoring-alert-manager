package com.dxc.monitoring.service.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.service.SystemSettingService;

/**
 * Fail-closed checks for monitoring scripts. Static scanning is defense in depth,
 * not a replacement for OS-level permissions and sandboxing.
 */
@Component
public class ScriptSecurityValidator
{
    private static final Pattern DANGEROUS_COMMAND = Pattern.compile(
            "(?im)(^|[;&|()\\s])(?:sudo|rm|rmdir|shred|mkfs(?:\\.[a-z0-9]+)?|wipefs|fdisk|parted|dd|"
            + "truncate|shutdown|reboot|poweroff|halt|killall|pkill|systemctl\\s+(?:stop|disable|mask)|"
            + "service\\s+\\S+\\s+stop|chmod|chown)(?=\\s|$)");

    private static final Pattern NESTED_SCRIPT = Pattern.compile(
            "(?im)(^|[;&|()\\s])(?:source|eval|exec|xargs|find\\s+[^\\n]*\\s-exec|"
            + "bash|sh|dash|zsh|ksh|python(?:[0-9.]*)?|perl|ruby|node)(?=\\s|$)"
            + "|(^|[;&|()\\s])\\.\\s+\\S+"
            + "|(^|[;&|()\\s])\\S+\\.sh(?=\\s|$)"
            + "|(?:curl|wget)\\b[^\\n]*\\|\\s*(?:ba)?sh\\b");

    private final Path[] fallbackAllowedDirectories;
    private final SystemSettingService systemSettingService;

    public ScriptSecurityValidator(
            @Value("${monitoring.security.script.allowed-directories:${user.dir}/scripts}") String allowedDirectories,
            SystemSettingService systemSettingService)
    {
        this.fallbackAllowedDirectories = toPaths(Arrays.asList(allowedDirectories.split(",")));
        this.systemSettingService = systemSettingService;
    }

    private Path[] toPaths(java.util.List<String> directories)
    {
        return directories.stream().map(String::trim).filter(value -> !value.isBlank())
                .map(Path::of).map(Path::toAbsolutePath).map(Path::normalize).toArray(Path[]::new);
    }

    private Path[] getAllowedDirectories()
    {
        java.util.List<String> configured = systemSettingService.getScriptAllowedDirectories();
        return configured.isEmpty() ? fallbackAllowedDirectories : toPaths(configured);
    }

    public Path validate(MonitoringJob job) throws IOException
    {
        if (job.getScriptPath() == null || job.getScriptPath().isBlank())
            throw new IllegalArgumentException("Script path is required for SCRIPT monitoring job.");

        Path configuredPath = Path.of(job.getScriptPath()).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(configuredPath))
            throw new IllegalArgumentException("Script security validation rejected a symbolic-link script.");

        if (!Files.exists(configuredPath) || !Files.isRegularFile(configuredPath) || !Files.isReadable(configuredPath))
            throw new IllegalArgumentException("Script security validation requires an existing, readable regular file.");

        Path realScript = configuredPath.toRealPath();
        Path allowedRoot = findAllowedRoot(realScript);
        if (allowedRoot == null)
            throw new IllegalArgumentException("Script security validation rejected a path outside configured allowed directories.");

        String approvedHash = job.getApprovedSha256();
        if (approvedHash == null || !approvedHash.matches("(?i)[a-f0-9]{64}"))
            throw new IllegalArgumentException("Execution blocked: Approved SHA-256 hash is not configured for this job.");

        String actualHash = sha256(realScript);
        if (!MessageDigest.isEqual(actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                approvedHash.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Execution blocked: Script SHA-256 validation failed. The script may have changed since approval.");

        String script = Files.readString(realScript);
        if (DANGEROUS_COMMAND.matcher(script).find())
            throw new IllegalArgumentException("Script security validation rejected a known dangerous command pattern.");

        if (NESTED_SCRIPT.matcher(script).find())
            throw new IllegalArgumentException("Script security validation rejected nested or indirect script execution patterns.");

        if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank())
        {
            Path workingDirectory = Path.of(job.getWorkingDirectory()).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(workingDirectory) || !Files.isDirectory(workingDirectory))
                throw new IllegalArgumentException("Script security validation rejected an invalid working directory.");
            Path realWorkingDirectory = workingDirectory.toRealPath();
            if (!realWorkingDirectory.startsWith(allowedRoot))
                throw new IllegalArgumentException("Script security validation rejected a working directory outside the allowed script directory.");
        }

        return realScript;
    }

    private Path findAllowedRoot(Path realScript) throws IOException
    {
        for (Path configuredRoot : getAllowedDirectories())
        {
            if (!Files.isDirectory(configuredRoot))
                continue;
            Path realRoot = configuredRoot.toRealPath();
            if (realScript.startsWith(realRoot))
                return realRoot;
        }
        return null;
    }

    public static String sha256(Path file) throws IOException
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }
}
