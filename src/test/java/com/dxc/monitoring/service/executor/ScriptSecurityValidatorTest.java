package com.dxc.monitoring.service.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.dxc.monitoring.config.ScriptValidationProperties;
import com.dxc.monitoring.entity.MonitoringJob;

class ScriptSecurityValidatorTest {
    @TempDir Path tempDir;

    @Test void acceptsScriptOnlyWhenPathAndHashAreConfigured() throws Exception {
        Path script=write("printf 'healthy\\n'\n");
        assertEquals(script.toRealPath(),new ScriptSecurityValidator(tempDir.toString()).validate(job(script)));
    }
    @Test void rejectsScriptWithoutConfiguredHash() throws Exception {
        Path script=write("echo healthy\n"); MonitoringJob job=job(script); job.setApprovedSha256(null);
        assertThrows(IllegalArgumentException.class,()->new ScriptSecurityValidator(tempDir.toString()).validate(job));
    }
    @Test void rejectsKnownDestructiveCommandEvenWhenHashMatches() throws Exception {
        Path script=write("rm -rf /tmp/example\n");
        assertThrows(IllegalArgumentException.class,()->new ScriptSecurityValidator(tempDir.toString()).validate(job(script)));
    }
    @Test void permitsOnlyExactConfiguredArgumentPattern() throws Exception {
        Path script=write("/bin/rm -f /tmp/report.tmp\n");
        ScriptValidationProperties config=new ScriptValidationProperties();
        var rule=new ScriptValidationProperties.AllowedCommandRule();
        rule.setCommand("rm"); rule.setAllowedArguments(List.of("-f","*.tmp"));
        config.setAllowedCommandRules(List.of(rule));
        assertEquals(script.toRealPath(),new ScriptSecurityValidator(tempDir.toString(),config).validate(job(script)));
    }
    @Test void doesNotPermitBroaderArgumentsOrPartialSubstringMatches() throws Exception {
        Path script=write("rm -rf /tmp/report.tmp\n");
        ScriptValidationProperties config=new ScriptValidationProperties();
        var rule=new ScriptValidationProperties.AllowedCommandRule();
        rule.setCommand("rm"); rule.setAllowedArguments(List.of("-f","*.tmp"));
        config.setAllowedCommandRules(List.of(rule));
        assertThrows(IllegalArgumentException.class,()->new ScriptSecurityValidator(tempDir.toString(),config).validate(job(script)));
    }
    @Test void ignoresDangerousTextInCommentsAndQuotedArguments() throws Exception {
        Path script=write("# rm -rf /\necho 'rm -rf /'\n");
        assertEquals(script.toRealPath(),new ScriptSecurityValidator(tempDir.toString()).validate(job(script)));
    }
    @Test void stillBlocksRestrictedCommandAfterPipelineAndWrappers() throws Exception {
        Path script=write("printf ok | env MODE=test command /usr/bin/rm -rf /tmp/x\n");
        assertThrows(IllegalArgumentException.class,()->new ScriptSecurityValidator(tempDir.toString()).validate(job(script)));
    }
    @Test void rejectsNestedScriptInvocation() throws Exception {
        Path script=write("sh helper.sh\n");
        assertThrows(IllegalArgumentException.class,()->new ScriptSecurityValidator(tempDir.toString()).validate(job(script)));
    }
    @Test void rejectsOverlyBroadAllowedRule() {
        ScriptValidationProperties config=new ScriptValidationProperties();
        var rule=new ScriptValidationProperties.AllowedCommandRule();
        rule.setCommand("rm"); rule.setAllowedArguments(List.of("*"));
        config.setAllowedCommandRules(List.of(rule));
        assertThrows(IllegalArgumentException.class,config::validate);
    }
    private Path write(String body) throws Exception {
        Path script=tempDir.resolve("health.sh"); Files.writeString(script,"#!/bin/sh\n"+body); return script;
    }
    private MonitoringJob job(Path script) {
        MonitoringJob job=new MonitoringJob(); job.setScriptPath(script.toString());
        try { job.setApprovedSha256(sha256(script)); } catch(Exception e) { throw new IllegalStateException(e); }
        return job;
    }
    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
