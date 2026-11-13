package com.dxc.monitoring.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dxc.monitoring.service.AlertConditionPreviewService;

/** Test-only endpoint for evaluating an unsaved condition against sample JSON. */
@RestController
@RequestMapping("/alerts/rules/conditions")
@PreAuthorize("hasAuthority('ALERT_CONFIG')")
public class AlertConditionPreviewController {
    private final AlertConditionPreviewService previewService;

    public AlertConditionPreviewController(AlertConditionPreviewService previewService) {
        this.previewService = previewService;
    }

    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody(required = false) PreviewRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Preview request is required."));
        }
        try {
            return ResponseEntity.ok(previewService.preview(request.sampleResponse(), request.condition()));
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message",
                    message == null || message.isBlank() ? "Unable to evaluate this condition." : message));
        }
    }

    public record PreviewRequest(String sampleResponse, String condition) {}
}
