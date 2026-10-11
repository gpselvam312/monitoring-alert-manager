package com.dxc.monitoring.service.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dxc.monitoring.entity.MonitoringJob;

class ScriptSecurityValidatorTest
{
    @TempDir
    Path tempDir;

    @Test
    void acceptsScriptOnlyWhenPathAndHashAreConfigured() throws Exception
    {
        Path script = tempDir.resolve("health.sh");
        Files.writeString(script, "#!/bin/sh\nprintf 'healthy\\n'\n");
        ScriptSecurityValidator validator = new ScriptSecurityValidator(tempDir.toString());

        assertEquals(script.toRealPath(), validator.validate(job(script)));
    }

    @Test
    void rejectsScriptWithoutConfiguredHash() throws Exception
    {
        Path script = tempDir.resolve("health.sh");
        Files.writeString(script, "#!/bin/sh\necho healthy\n");
        ScriptSecurityValidator validator = new ScriptSecurityValidator(tempDir.toString());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(job(script)));
    }

    @Test
    void rejectsKnownDestructiveCommandEvenWhenHashMatches() throws Exception
    {
        Path script = tempDir.resolve("health.sh");
        Files.writeString(script, "#!/bin/sh\nrm -rf /tmp/example\n");
        ScriptSecurityValidator validator = new ScriptSecurityValidator(tempDir.toString(),
                script.toAbsolutePath() + "=" + sha256(script));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(job(script)));
    }

    @Test
    void rejectsNestedScriptInvocationEvenWhenHashMatches() throws Exception
    {
        Path script = tempDir.resolve("health.sh");
        Files.writeString(script, "#!/bin/sh\nsh helper.sh\n");
        ScriptSecurityValidator validator = new ScriptSecurityValidator(tempDir.toString(),
                script.toAbsolutePath() + "=" + sha256(script));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(job(script)));
    }

    private MonitoringJob job(Path script)
    {
        MonitoringJob job = new MonitoringJob();
        job.setScriptPath(script.toString());
        try { job.setApprovedSha256(sha256(script)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
        return job;
    }

    private String sha256(Path path) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
