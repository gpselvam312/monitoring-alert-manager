package com.dxc.monitoring.service.streaming;

import java.time.OffsetDateTime;

public record StreamingJobView(
        Long id,
        String name,
        String description,
        String machineName,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime deadlineAt,
        String outputBuffer,
        String errorMessage,
        long remainingSeconds)
{
}
