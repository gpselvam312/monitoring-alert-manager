package com.dxc.monitoring.service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;
import com.dxc.monitoring.repository.NotificationChannelRepository;
import com.dxc.monitoring.service.notification.NotificationChannelSender;

@Service
public class NotificationChannelTestService {
    private static final Logger log = LoggerFactory.getLogger(NotificationChannelTestService.class);

    private final NotificationChannelRepository channelRepository;
    private final NotificationChannelSecretService secretService;
    private final List<NotificationChannelSender> senders;

    public NotificationChannelTestService(NotificationChannelRepository channelRepository,
            NotificationChannelSecretService secretService, List<NotificationChannelSender> senders) {
        this.channelRepository = channelRepository;
        this.secretService = secretService;
        this.senders = senders;
    }

    @Transactional(readOnly = true)
    public void sendTest(Long channelId, String emailAddress, String phoneNumber) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Notification channel was not found."));
        NotificationRecipient destination = new NotificationRecipient();
        switch (channel.getChannelType()) {
            case EMAIL -> {
                String email = clean(emailAddress);
                if (email == null || email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new IllegalArgumentException("Enter a valid test email address.");
                }
                destination.setEmail(email);
                validateEmailSettings(channel);
            }
            case TEAMS -> {
                // Teams delivers to the webhook configured for this channel; there is no separate destination.
            }
            case WHATSAPP -> {
                String phone = clean(phoneNumber);
                String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
                if (digits.length() < 8 || digits.length() > 15) {
                    throw new IllegalArgumentException("Enter a valid WhatsApp test number with country code.");
                }
                destination.setPhoneNumber(digits);
            }
        }

        NotificationChannelSender sender = senders.stream()
                .filter(candidate -> candidate.channelType() == channel.getChannelType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No sender is configured for this channel type."));

        String secretKey = channel.getCredentialReference();
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = switch (channel.getChannelType()) {
                case EMAIL -> "smtp-password";
                case TEAMS -> "webhook-url";
                case WHATSAPP -> "access-token";
            };
        }
        String credential = secretService.getSecret(channel.getId(), secretKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Monitoring Alert Manager - Test Notification");
        payload.put("body", "This is a test notification from Monitoring Alert Manager. No alert was created.");
        payload.put("severity", "INFO");
        payload.put("application", "Monitoring Alert Manager");
        payload.put("environment", "Test");
        payload.put("monitoringJob", "Notification channel test");
        payload.put("alertId", "N/A");

        try {
            sender.send(channel, destination, payload, credential);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            String rootType = rootCause(exception).getClass().getSimpleName();
            // Log only exception types and channel metadata; never log exception messages, credentials,
            // SMTP configuration, webhook URLs, or provider request/response bodies.
            log.warn("Test notification failed: channelId={}, channelType={}, exceptionType={}, rootCauseType={}",
                    channel.getId(), channel.getChannelType(), exception.getClass().getSimpleName(), rootType);
            throw new IllegalStateException(safeFailureMessage(channel, exception));
        }
    }

    private void validateEmailSettings(NotificationChannel channel) {
        Map<String, Object> config;
        try {
            config = new tools.jackson.databind.ObjectMapper()
                    .readValue(channel.getConfiguration(),
                            new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("Saved email channel configuration is invalid. Reopen and save the channel settings.");
        }
        String host = string(config, "host");
        String from = string(config, "from");
        String username = string(config, "username");
        boolean auth = Boolean.parseBoolean(string(config, "auth"));
        if (host == null || host.isBlank() || from == null || from.isBlank()) {
            throw new IllegalArgumentException("Email channel requires an SMTP host and sender address. Update and save the channel first.");
        }
        if (auth && (username == null || username.isBlank())) {
            throw new IllegalArgumentException("SMTP authentication is enabled, but the username is missing. Update and save the channel.");
        }
        if (auth && secretService.getSecret(channel.getId(), "smtp-password") == null) {
            throw new IllegalArgumentException("SMTP authentication is enabled, but no saved SMTP password was found. Edit the channel and enter the password again.");
        }
    }

    private String safeFailureMessage(NotificationChannel channel, Exception exception) {
        if (channel.getChannelType() == NotificationChannel.ChannelType.EMAIL) {
            if (hasCause(exception, MailAuthenticationException.class)
                    || hasCauseNamed(exception, "AuthenticationFailedException")) {
                return "SMTP authentication failed. Verify the SMTP username and password or app password, then save and test again.";
            }
            if (hasCause(exception, UnknownHostException.class)) {
                return "The SMTP host could not be resolved. Verify the SMTP host name and DNS/network access from the application server.";
            }
            if (hasCause(exception, ConnectException.class)) {
                return "Connection to the SMTP server was refused. Verify the SMTP host and port, and confirm outbound SMTP access is allowed.";
            }
            if (hasCause(exception, SocketTimeoutException.class)
                    || hasCauseNamed(exception, "MailConnectException")) {
                return "Connection to the SMTP server timed out. Verify the host and port, TLS requirements, firewall rules, and outbound SMTP access.";
            }
            if (hasCause(exception, SSLException.class)
                    || hasCauseNamed(exception, "SSLHandshakeException")) {
                return "The SMTP TLS handshake failed. Check whether the selected port and STARTTLS setting match your provider's SMTP requirements.";
            }
            if (exception instanceof MailException || hasCauseNamed(exception, "MessagingException")) {
                return "The SMTP server rejected or could not process the test email. Verify sender address, authentication, port, and TLS settings; check application logs for the failure category.";
            }
        }
        return "The test notification could not be sent. Check the saved channel settings and provider connectivity. Application logs contain a sanitized failure category.";
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private boolean hasCauseNamed(Throwable exception, String simpleName) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (simpleName.equals(current.getClass().getSimpleName())) return true;
        }
        return false;
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private String string(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
