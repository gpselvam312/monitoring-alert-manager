package com.dxc.monitoring.service.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Conservative shell token scanner. It is not a shell parser; unsupported
 * constructs must be rejected by the validator rather than treated as safe.
 */
final class ShellCommandScanner {
    private static final Set<String> CONTROL_BOUNDARIES =
            Set.of("then", "do", "else", "elif", "fi", "done");

    private ShellCommandScanner() {}

    static List<List<String>> commands(String source) {
        List<List<String>> result = new ArrayList<>();
        List<String> command = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (comment) {
                if (c == '\n') {
                    comment = false;
                    flush(token, command);
                    finish(command, result);
                }
                continue;
            }
            if (escaped) {
                token.append(c);
                escaped = false;
                continue;
            }
            if (quote != 0) {
                if (quote != '\'' && (c == '`' || (c == '$' && i + 1 < source.length()
                        && source.charAt(i + 1) == '('))) {
                    throw new IllegalArgumentException("Script security validation rejected command substitution.");
                }
                if (c == quote) quote = 0;
                else token.append(c);
                continue;
            }
            if (c == '`' || (c == '$' && i + 1 < source.length() && source.charAt(i + 1) == '(')) {
                throw new IllegalArgumentException("Script security validation rejected command substitution.");
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                continue;
            }
            if (c == '#' && token.length() == 0) {
                comment = true;
                continue;
            }
            if (c == '<' || c == '>') {
                flush(token, command);
                command.add(String.valueOf(c));
                continue;
            }
            if (c == ';' || c == '|' || c == '&' || c == '\n' || c == '\r' || c == '(' || c == ')') {
                flush(token, command);
                finish(command, result);
                continue;
            }
            if (Character.isWhitespace(c)) {
                flush(token, command);
                if (!command.isEmpty()
                        && CONTROL_BOUNDARIES.contains(command.get(command.size() - 1).toLowerCase(java.util.Locale.ROOT))) {
                    finish(command, result);
                }
                continue;
            }
            token.append(c);
        }
        if (quote != 0 || escaped) {
            throw new IllegalArgumentException("Script security validation rejected an incomplete quoted or escaped token.");
        }
        flush(token, command);
        finish(command, result);
        return result;
    }

    private static void flush(StringBuilder token, List<String> command) {
        if (token.length() > 0) {
            command.add(token.toString());
            token.setLength(0);
        }
    }

    private static void finish(List<String> command, List<List<String>> result) {
        if (!command.isEmpty()) {
            result.add(List.copyOf(command));
            command.clear();
        }
    }
}
