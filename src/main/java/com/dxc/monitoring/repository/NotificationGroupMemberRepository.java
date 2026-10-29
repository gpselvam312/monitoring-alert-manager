package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.NotificationGroupMember;

public interface NotificationGroupMemberRepository extends JpaRepository<NotificationGroupMember, com.dxc.monitoring.entity.NotificationGroupMemberId> {
    @EntityGraph(attributePaths = {"recipient"})
    List<NotificationGroupMember> findByGroup_IdAndRecipient_EnabledTrue(Long groupId);

    @EntityGraph(attributePaths = {"recipient"})
    List<NotificationGroupMember> findByGroup_Id(Long groupId);

    void deleteByGroup_Id(Long groupId);

    void deleteByRecipient_Id(Long recipientId);
}
