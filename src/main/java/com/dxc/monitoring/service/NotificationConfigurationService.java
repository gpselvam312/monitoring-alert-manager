package com.dxc.monitoring.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationGroup;
import com.dxc.monitoring.entity.NotificationGroupMember;
import com.dxc.monitoring.entity.NotificationGroupMemberId;
import com.dxc.monitoring.entity.NotificationPolicy;
import com.dxc.monitoring.entity.NotificationRecipient;
import com.dxc.monitoring.entity.NotificationRoute;
import com.dxc.monitoring.repository.NotificationChannelRepository;
import com.dxc.monitoring.repository.NotificationGroupMemberRepository;
import com.dxc.monitoring.repository.NotificationGroupRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.repository.NotificationRecipientRepository;
import com.dxc.monitoring.repository.NotificationRouteRepository;
import com.dxc.monitoring.web.NotificationChannelForm;

import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationConfigurationService {
    private final NotificationChannelRepository channelRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationGroupRepository groupRepository;
    private final NotificationGroupMemberRepository groupMemberRepository;
    private final NotificationPolicyRepository policyRepository;
    private final NotificationRouteRepository routeRepository;
    private final NotificationChannelSecretService secretService;
    private final ObjectMapper objectMapper;

    public NotificationConfigurationService(NotificationChannelRepository channelRepository,
            NotificationRecipientRepository recipientRepository, NotificationGroupRepository groupRepository,
            NotificationGroupMemberRepository groupMemberRepository, NotificationPolicyRepository policyRepository,
            NotificationRouteRepository routeRepository, NotificationChannelSecretService secretService,
            ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.recipientRepository = recipientRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.policyRepository = policyRepository;
        this.routeRepository = routeRepository;
        this.secretService = secretService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationChannel createChannel(NotificationChannelForm form, Long userId) {
        if (form.getName() == null || form.getName().isBlank() || form.getName().trim().length() > 100) {
            throw new IllegalArgumentException("Channel name is required and must not exceed 100 characters.");
        }
        NotificationChannel.ChannelType type;
        try { type = NotificationChannel.ChannelType.valueOf(form.getChannelType()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Choose Email, Teams or WhatsApp."); }

        Map<String, Object> config = new LinkedHashMap<>();
        String secretKey = null;
        String secret = form.getSecret() == null ? "" : form.getSecret();
        switch (type) {
            case EMAIL -> {
                String host = required(form.getSmtpHost(), "SMTP host");
                String from = required(form.getSmtpFrom(), "Sender email address");
                if (form.getSmtpPort() < 1 || form.getSmtpPort() > 65535) {
                    throw new IllegalArgumentException("SMTP port must be between 1 and 65535.");
                }
                if (form.isSmtpAuth() && (form.getSmtpUsername() == null || form.getSmtpUsername().isBlank() || secret.isBlank())) {
                    throw new IllegalArgumentException("SMTP authentication requires a username and password.");
                }
                config.put("host", host);
                config.put("port", form.getSmtpPort());
                config.put("from", from);
                config.put("username", trimToNull(form.getSmtpUsername()));
                config.put("auth", form.isSmtpAuth());
                config.put("startTls", form.isSmtpStartTls());
                if (!secret.isBlank()) secretKey = "smtp-password";
            }
            case TEAMS -> {
                if (secret.isBlank()) throw new IllegalArgumentException("Paste the Teams webhook URL; it will be encrypted before storage.");
                secret = secret.trim();
                validateTeamsWebhook(secret);
                secretKey = "webhook-url";
            }
            case WHATSAPP -> {
                String phoneNumberId = required(form.getWhatsappPhoneNumberId(), "WhatsApp phone number ID");
                if (!phoneNumberId.matches("[0-9]{5,30}")) throw new IllegalArgumentException("WhatsApp phone number ID must be numeric.");
                String apiVersion = trimToNull(form.getWhatsappApiVersion());
                if (apiVersion == null) apiVersion = "v23.0";
                if (!apiVersion.matches("v[0-9]{2,3}\\.[0-9]+")) throw new IllegalArgumentException("WhatsApp API version is invalid.");
                if (secret.isBlank()) throw new IllegalArgumentException("WhatsApp Cloud API access token is required.");
                secret = secret.trim();
                config.put("phoneNumberId", phoneNumberId);
                config.put("apiVersion", apiVersion);
                config.put("templateName", required(form.getWhatsappTemplateName(), "Approved WhatsApp template name"));
                config.put("languageCode", trimToNull(form.getWhatsappLanguageCode()) == null ? "en_US" : form.getWhatsappLanguageCode().trim());
                secretKey = "access-token";
            }
        }

        NotificationChannel channel = new NotificationChannel();
        channel.setName(form.getName().trim());
        channel.setChannelType(type);
        channel.setEnabled(true);
        channel.setConfiguration(toJson(config));
        channel.setCredentialReference(secretKey);
        channel.setCreatedBy(userId);
        channel.setUpdatedBy(userId);
        channel = channelRepository.save(channel);
        if (secretKey != null) secretService.saveSecret(channel.getId(), secretKey, secret, userId);
        return channel;
    }

    @Transactional
    public NotificationRecipient createRecipient(String name, String email, String phone, Long userId) {
        String cleanName = required(name, "Recipient name");
        String cleanEmail = trimToNull(email);
        String cleanPhone = trimToNull(phone);
        if (cleanEmail == null && cleanPhone == null) {
            throw new IllegalArgumentException("Provide an email address, a WhatsApp phone number, or both.");
        }
        if (cleanEmail != null && cleanEmail.length() > 255) throw new IllegalArgumentException("Email address is too long.");
        if (cleanPhone != null && cleanPhone.length() > 50) throw new IllegalArgumentException("Phone number is too long.");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setName(cleanName);
        recipient.setEmail(cleanEmail);
        recipient.setPhoneNumber(cleanPhone);
        recipient.setEnabled(true);
        recipient.setCreatedBy(userId);
        recipient.setUpdatedBy(userId);
        return recipientRepository.save(recipient);
    }

    @Transactional
    public NotificationGroup createGroup(String name, String description, List<Long> recipientIds, Long userId) {
        NotificationGroup group = new NotificationGroup();
        group.setName(required(name, "Group name"));
        group.setDescription(trimToNull(description));
        group.setEnabled(true);
        group.setCreatedBy(userId);
        group.setUpdatedBy(userId);
        group = groupRepository.save(group);
        if (recipientIds != null) {
            for (Long recipientId : recipientIds.stream().filter(id -> id != null).distinct().toList()) {
                NotificationRecipient recipient = recipientRepository.findById(recipientId)
                        .filter(NotificationRecipient::isEnabled)
                        .orElseThrow(() -> new IllegalArgumentException("A selected recipient is unavailable."));
                NotificationGroupMember member = new NotificationGroupMember();
                member.setId(new NotificationGroupMemberId(group.getId(), recipient.getId()));
                member.setGroup(group);
                member.setRecipient(recipient);
                groupMemberRepository.save(member);
            }
        }
        return group;
    }

    @Transactional
    public NotificationPolicy createPolicy(String name, String description, Long userId) {
        NotificationPolicy policy = new NotificationPolicy();
        policy.setName(required(name, "Policy name"));
        policy.setDescription(trimToNull(description));
        policy.setEnabled(true);
        policy.setCreatedBy(userId);
        policy.setUpdatedBy(userId);
        return policyRepository.save(policy);
    }

    @Transactional
    public NotificationRoute createRoute(Long policyId, Long channelId, String severityValue,
            String eventTypeValue, String targetType, Long recipientId, Long groupId,
            int minimumIntervalSeconds) {
        if (minimumIntervalSeconds < 0 || minimumIntervalSeconds > 86400) {
            throw new IllegalArgumentException("Minimum notification interval must be between 0 and 86400 seconds.");
        }
        NotificationPolicy policy = policyRepository.findById(policyId).filter(NotificationPolicy::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Select an enabled notification policy."));
        NotificationChannel channel = channelRepository.findById(channelId).filter(NotificationChannel::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Select an enabled notification channel."));
        NotificationRoute route = new NotificationRoute();
        route.setNotificationPolicy(policy);
        route.setChannel(channel);
        try {
            route.setSeverity(AlertRule.Severity.valueOf(severityValue));
            route.setEventType(NotificationRoute.EventType.valueOf(eventTypeValue));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Choose a supported severity and notification event.");
        }

        if ("RECIPIENT".equals(targetType)) {
            if (recipientId == null || groupId != null) throw new IllegalArgumentException("Select exactly one recipient or group.");
            NotificationRecipient recipient = recipientRepository.findById(recipientId)
                    .filter(NotificationRecipient::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Select an enabled recipient."));
            validateRecipientDestination(channel, recipient);
            route.setRecipient(recipient);
        } else if ("GROUP".equals(targetType)) {
            if (groupId == null || recipientId != null) throw new IllegalArgumentException("Select exactly one recipient or group.");
            NotificationGroup group = groupRepository.findById(groupId).filter(NotificationGroup::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Select an enabled recipient group."));
            List<NotificationGroupMember> members = groupMemberRepository.findByGroup_IdAndRecipient_EnabledTrue(group.getId());
            boolean hasDestination = members.stream().map(NotificationGroupMember::getRecipient)
                    .anyMatch(recipient -> hasDestination(channel, recipient));
            if (!hasDestination) {
                throw new IllegalArgumentException("The selected group has no enabled member with a destination for this channel.");
            }
            route.setGroup(group);
        } else {
            throw new IllegalArgumentException("Choose a recipient or recipient group.");
        }
        route.setEnabled(true);
        route.setMinimumIntervalSeconds(minimumIntervalSeconds);
        return routeRepository.save(route);
    }


    @Transactional
    public void toggleChannel(Long id, Long userId) {
        NotificationChannel value = channelRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification channel was not found."));
        value.setEnabled(!value.isEnabled());
        value.setUpdatedBy(userId);
        channelRepository.save(value);
    }

    @Transactional
    public void toggleRecipient(Long id, Long userId) {
        NotificationRecipient value = recipientRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Recipient was not found."));
        value.setEnabled(!value.isEnabled());
        value.setUpdatedBy(userId);
        recipientRepository.save(value);
    }

    @Transactional
    public void toggleGroup(Long id, Long userId) {
        NotificationGroup value = groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Recipient group was not found."));
        value.setEnabled(!value.isEnabled());
        value.setUpdatedBy(userId);
        groupRepository.save(value);
    }

    @Transactional
    public void togglePolicy(Long id, Long userId) {
        NotificationPolicy value = policyRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification policy was not found."));
        value.setEnabled(!value.isEnabled());
        value.setUpdatedBy(userId);
        policyRepository.save(value);
    }

    @Transactional
    public void toggleRoute(Long id) {
        NotificationRoute value = routeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Notification route was not found."));
        value.setEnabled(!value.isEnabled());
        routeRepository.save(value);
    }

    private void validateRecipientDestination(NotificationChannel channel, NotificationRecipient recipient) {
        if (!hasDestination(channel, recipient)) {
            throw new IllegalArgumentException("The selected recipient has no destination configured for this channel type.");
        }
    }

    private boolean hasDestination(NotificationChannel channel, NotificationRecipient recipient) {
        return switch (channel.getChannelType()) {
            case EMAIL -> recipient.getEmail() != null && !recipient.getEmail().isBlank();
            case WHATSAPP -> recipient.getPhoneNumber() != null && !recipient.getPhoneNumber().isBlank();
            case TEAMS -> true;
        };
    }

    private void validateTeamsWebhook(String webhook) {
        try {
            java.net.URI uri = java.net.URI.create(webhook);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
            boolean trusted = host.equals("outlook.office.com") || host.equals("outlook.office365.com")
                    || host.endsWith(".webhook.office.com") || host.endsWith(".webhook.office365.com")
                    || host.endsWith(".logic.azure.com") || host.endsWith(".powerplatform.com");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !trusted || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Teams webhook must be an HTTPS Microsoft Teams webhook URL.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Teams webhook URL is invalid or uses an unsupported host.");
        }
    }

    private String required(String value, String label) {
        String clean = trimToNull(value);
        if (clean == null) throw new IllegalArgumentException(label + " is required.");
        return clean;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Notification configuration could not be serialized."); }
    }
}
