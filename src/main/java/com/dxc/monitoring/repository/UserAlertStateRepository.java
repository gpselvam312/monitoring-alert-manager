package com.dxc.monitoring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.UserAlertState;
import com.dxc.monitoring.entity.UserAlertStateId;

public interface UserAlertStateRepository extends JpaRepository<UserAlertState, UserAlertStateId> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ra_fcb.user_alert_states AS current_state (user_id, alert_id, read_at, updated_at)
            VALUES (:userId, :alertId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, alert_id)
            DO UPDATE SET read_at = COALESCE(current_state.read_at, EXCLUDED.read_at),
                          updated_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int markRead(@Param("userId") Long userId, @Param("alertId") Long alertId);
}
