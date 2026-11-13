package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.dxc.monitoring.entity.NotificationGroup;

public interface NotificationGroupRepository extends JpaRepository<NotificationGroup, Long> {
    List<NotificationGroup> findAllByOrderByNameAsc();
    List<NotificationGroup> findByEnabledTrueOrderByNameAsc();
    List<NotificationGroup> findByEnabledTrueAndChannelTypeOrderByNameAsc(NotificationGroup.ChannelType channelType);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM ra_fcb.alert_rule_notification_routes WHERE group_id = :groupId UNION ALL SELECT 1 FROM ra_fcb.alert_rule_notification_route_groups WHERE group_id = :groupId UNION ALL SELECT 1 FROM ra_fcb.notification_routes WHERE group_id = :groupId)", nativeQuery = true)
    boolean existsReferencedByAnyRoute(@Param("groupId") Long groupId);
}
