package com.dxc.monitoring.service.executor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Drains a process stream fully while retaining only a bounded prefix. */
final class ProcessOutputCapture {
    private static final String TRUNCATION_MARKER = "\n[output truncated; retained limit reached]";

    private ProcessOutputCapture() {
    }

    static CapturedOutput capture(InputStream input, int maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("Maximum captured output must be positive.");
        ByteArrayOutputStream retained = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        boolean truncated = false;
        int count;
        while ((count = input.read(buffer)) != -1) {
            int remaining = maxBytes - retained.size();
            if (remaining > 0) retained.write(buffer, 0, Math.min(remaining, count));
            if (count > remaining) truncated = true;
        }
        return new CapturedOutput(retained.toByteArray(), truncated);
    }

    record CapturedOutput(byte[] bytes, boolean truncated) {
        String text() {
            String value = new String(bytes, StandardCharsets.UTF_8);
            return truncated ? value + TRUNCATION_MARKER : value;
        }
    }
}
