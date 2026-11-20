package com.dxc.monitoring.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "monitoring.security.script.validation")
public class ScriptValidationProperties {
    private boolean enabled = true;
    private List<String> restrictedCommands = new ArrayList<>(List.of("sudo","rm","rmdir","shred","mkfs","wipefs","fdisk","parted","dd","truncate","shutdown","reboot","poweroff","halt","killall","pkill","systemctl","service","chmod","chown"));
    private List<AllowedCommandRule> allowedCommandRules = new ArrayList<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<String> getRestrictedCommands() { return restrictedCommands; }
    public void setRestrictedCommands(List<String> values) {
        this.restrictedCommands = values == null ? new ArrayList<>() : values.stream()
                .filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toLowerCase(Locale.ROOT)).distinct().toList();
    }
    public List<AllowedCommandRule> getAllowedCommandRules() { return allowedCommandRules; }
    public void setAllowedCommandRules(List<AllowedCommandRule> rules) {
        this.allowedCommandRules = rules == null ? new ArrayList<>() : new ArrayList<>(rules);
    }
    public void validate() {
        for (AllowedCommandRule rule : allowedCommandRules) {
            if (rule.command == null || rule.command.isBlank())
                throw new IllegalArgumentException("Script allowed-command rule requires a command.");
            String command = rule.command.trim().toLowerCase(Locale.ROOT);
            if (!restrictedCommands.contains(command) || command.equals("sudo"))
                throw new IllegalArgumentException("Script allowed-command rule must target a restricted non-sudo command.");
            if (rule.allowedArguments == null || rule.allowedArguments.isEmpty())
                throw new IllegalArgumentException("Script allowed-command rule requires exact argument patterns.");
            for (String pattern : rule.allowedArguments) {
                if (pattern == null || pattern.isBlank() || pattern.trim().equals("*")
                        || pattern.matches(".*[;|&$<>\u0060].*"))
                    throw new IllegalArgumentException("Script allowed-command argument patterns are invalid or too broad.");
            }
        }
    }
    public static class AllowedCommandRule {
        private String command;
        private List<String> allowedArguments = new ArrayList<>();
        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public List<String> getAllowedArguments() { return allowedArguments; }
        public void setAllowedArguments(List<String> allowedArguments) { this.allowedArguments = allowedArguments; }
    }
}
