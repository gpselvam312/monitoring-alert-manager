package com.dxc.monitoring.service.executor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.config.ScriptValidationProperties;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.service.SystemSettingService;

@Component
public class ScriptSecurityValidator {
    private static final Logger log = LoggerFactory.getLogger(ScriptSecurityValidator.class);
    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*");
    private static final Set<String> SHELL_WORDS = Set.of("if","then","else","elif","fi","do","done","while","until","!");
    private static final Set<String> WRAPPERS = Set.of("env","command","builtin","nohup","time");
    private static final Set<String> NESTED = Set.of("source","eval","exec","xargs","bash","sh","dash","zsh","ksh","python","python2","python3","perl","ruby","node","nodejs","trap","busybox","nice","timeout","stdbuf","setsid","chrt","ionice");
    private final Path[] fallbackAllowedDirectories;
    private final SystemSettingService systemSettingService;
    private final ScriptValidationProperties validation;

    @Autowired
    public ScriptSecurityValidator(
            @Value("${monitoring.security.script.allowed-directories:${user.dir}/scripts}") String allowedDirectories,
            SystemSettingService systemSettingService, ScriptValidationProperties validation) {
        this.fallbackAllowedDirectories = toPaths(Arrays.asList(allowedDirectories.split(",")));
        this.systemSettingService = systemSettingService;
        this.validation = validation;
        this.validation.validate();
    }

    ScriptSecurityValidator(String allowedDirectories) {
        this(allowedDirectories, null, new ScriptValidationProperties());
    }

    ScriptSecurityValidator(String allowedDirectories, ScriptValidationProperties validation) {
        this(allowedDirectories, null, validation);
    }

    private Path[] toPaths(List<String> directories) {
        return directories.stream().map(String::trim).filter(v -> !v.isBlank()).map(Path::of)
                .map(Path::toAbsolutePath).map(Path::normalize).toArray(Path[]::new);
    }

    private Path[] getAllowedDirectories() {
        if (systemSettingService == null) return fallbackAllowedDirectories;
        try {
            List<String> configured = systemSettingService.getScriptAllowedDirectories();
            return configured.isEmpty() ? fallbackAllowedDirectories : toPaths(configured);
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("Script security configuration is invalid.");
        }
    }

    public Path validate(MonitoringJob job) throws IOException {
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
        validateCommands(script);
        if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank()) {
            Path workingDirectory = Path.of(job.getWorkingDirectory()).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(workingDirectory) || !Files.isDirectory(workingDirectory))
                throw new IllegalArgumentException("Script security validation rejected an invalid working directory.");
            Path realWorkingDirectory = workingDirectory.toRealPath();
            if (!realWorkingDirectory.startsWith(allowedRoot))
                throw new IllegalArgumentException("Script security validation rejected a working directory outside the allowed script directory.");
        }
        return realScript;
    }

    private void validateCommands(String script) {
        validation.validate();
        for (List<String> tokens : ShellCommandScanner.commands(script)) {
            int index=0;
            while (index<tokens.size() && SHELL_WORDS.contains(tokens.get(index).toLowerCase(Locale.ROOT))) index++;
            while (index<tokens.size() && ASSIGNMENT.matcher(tokens.get(index)).matches()) index++;
            while (index<tokens.size() && WRAPPERS.contains(base(tokens.get(index)))) {
                String wrapper=base(tokens.get(index++));
                while (index<tokens.size() && (tokens.get(index).startsWith("-") || ASSIGNMENT.matcher(tokens.get(index)).matches())) index++;
                if (wrapper.equals("env") || wrapper.equals("command") || wrapper.equals("builtin")) continue;
            }
            while (index<tokens.size()) {
                if (tokens.get(index).matches("[0-9]+") && index+1<tokens.size()
                        && (tokens.get(index+1).equals(">") || tokens.get(index+1).equals("<"))) {
                    index += 2;
                    if (index<tokens.size()) index++;
                } else if (tokens.get(index).equals(">") || tokens.get(index).equals("<")) {
                    index++;
                    if (index<tokens.size()) index++;
                } else break;
            }
            if (index>=tokens.size()) continue;
            String command=base(tokens.get(index));
            if (Set.of("case", "function", "select", "coproc").contains(command))
                throw new IllegalArgumentException("Script security validation rejected unsupported shell control syntax.");
            if (command.equals("env") && tokens.subList(index + 1, tokens.size()).stream()
                    .anyMatch(arg -> arg.equals("-S") || arg.startsWith("-S") || arg.startsWith("--split-string")))
                throw new IllegalArgumentException("Script security validation rejected env split-string execution.");
            if (command.contains("$") || command.contains("`"))
                throw new IllegalArgumentException("Script security validation rejected a dynamically selected command.");
            if (command.isBlank() || command.startsWith(">") || command.startsWith("<")) continue;
            if (isNested(command) || command.endsWith(".sh") || command.equals("."))
                throw new IllegalArgumentException("Script security validation rejected nested or indirect script execution.");
            if (command.equals("find") && tokens.subList(index+1,tokens.size()).stream()
                    .anyMatch(arg -> Set.of("-exec", "-execdir", "-ok", "-okdir").contains(arg)))
                throw new IllegalArgumentException("Script security validation rejected find command execution actions.");
            if ((command.equals("awk") || command.equals("gawk"))
                    && Pattern.compile("(?i)\\\\bsystem\\\\s*\\\\(")
                            .matcher(String.join(" ", tokens.subList(index + 1, tokens.size()))).find())
                throw new IllegalArgumentException("Script security validation rejected awk system() command execution.");
            boolean restricted=validation.getRestrictedCommands().contains(command)
                    || (command.startsWith("mkfs.") && validation.getRestrictedCommands().contains("mkfs"));
            if (command.equals("systemctl"))
                restricted=tokens.size()>index+1 && Set.of("start","stop","restart","disable","mask").contains(tokens.get(index+1));
            if (command.equals("service"))
                restricted=tokens.size()>index+2 && Set.of("stop","restart").contains(tokens.get(index+2));
            if (restricted && validation.isEnabled() && !isAllowed(command,tokens.subList(index+1,tokens.size()))) {
                throw new IllegalArgumentException("Script security validation rejected restricted command '" + command + "'.");
            }
        }
    }

    private boolean isNested(String command) {
        return NESTED.contains(command) || command.matches("python[0-9.]*");
    }

    private boolean isAllowed(String command,List<String> arguments) {
        for (var rule:validation.getAllowedCommandRules()) {
            if (!rule.getCommand().equalsIgnoreCase(command) || rule.getAllowedArguments().size()!=arguments.size()) continue;
            boolean matches=true;
            for (int i=0;i<arguments.size();i++) {
                if (!glob(rule.getAllowedArguments().get(i).trim()).matcher(arguments.get(i)).matches()) { matches=false; break; }
            }
            if (matches) {
                log.warn("Script validation allowed restricted command '{}' under configured argument rule.",command);
                return true;
            }
        }
        return false;
    }

    private Pattern glob(String value) {
        StringBuilder regex=new StringBuilder("^");
        for(char c:value.toCharArray()) {
            if(c=='*') regex.append(".*");
            else if(c=='?') regex.append('.');
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.append("$").toString());
    }

    private String base(String token) {
        String value=token.toLowerCase(Locale.ROOT);
        int slash=value.lastIndexOf('/');
        return slash>=0?value.substring(slash+1):value;
    }

    private Path findAllowedRoot(Path realScript) throws IOException {
        for (Path configuredRoot:getAllowedDirectories()) {
            if (!Files.isDirectory(configuredRoot)) continue;
            Path realRoot=configuredRoot.toRealPath();
            if (realScript.startsWith(realRoot)) return realRoot;
        }
        return null;
    }

    public static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.",exception);
        }
    }
}
