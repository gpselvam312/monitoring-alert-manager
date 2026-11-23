package com.dxc.monitoring.service.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ProcessOutputCaptureTest {
    @Test
    void retainsAllOutputWhenBelowLimit() throws IOException {
        byte[] source = "script output".getBytes(StandardCharsets.UTF_8);
        ProcessOutputCapture.CapturedOutput captured =
                ProcessOutputCapture.capture(new ByteArrayInputStream(source), 100);
        assertEquals("script output", captured.text());
        assertEquals(source.length, captured.bytes().length);
        assertTrue(!captured.truncated());
    }

    @Test
    void truncatesRetainedOutputButContinuesDrainingStream() throws IOException {
        byte[] source = "0123456789".getBytes(StandardCharsets.UTF_8);
        CountingInputStream input = new CountingInputStream(source);
        ProcessOutputCapture.CapturedOutput captured = ProcessOutputCapture.capture(input, 4);
        assertEquals(4, captured.bytes().length);
        assertTrue(captured.truncated());
        assertEquals(source.length, input.bytesRead);
        assertTrue(captured.text().contains("[output truncated"));
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> ProcessOutputCapture.capture(new ByteArrayInputStream(new byte[0]), 0));
    }

    private static final class CountingInputStream extends InputStream {
        private final byte[] source;
        private int offset;
        private int bytesRead;

        private CountingInputStream(byte[] source) { this.source = source; }

        @Override
        public int read() {
            if (offset >= source.length) return -1;
            bytesRead++;
            return source[offset++] & 0xff;
        }

        @Override
        public int read(byte[] target, int start, int length) {
            if (offset >= source.length) return -1;
            int count = Math.min(length, source.length - offset);
            System.arraycopy(source, offset, target, start, count);
            offset += count;
            bytesRead += count;
            return count;
        }
    }
}
