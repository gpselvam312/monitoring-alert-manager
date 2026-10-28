package com.dxc.monitoring.service.notification;

import java.util.Map;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;

public interface NotificationChannelSender {
    NotificationChannel.ChannelType channelType();
    String send(NotificationChannel channel, NotificationRecipient recipient,
            Map<String, Object> payload, String credential);
}
