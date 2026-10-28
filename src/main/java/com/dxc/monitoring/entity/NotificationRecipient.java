package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "notification_recipients", schema = "ra_fcb")
public class NotificationRecipient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "user_id")
    private Long userId;
    @Column(length = 255)
    private String email;
    @Column(name = "phone_number", length = 50)
    private String phoneNumber;
    @Column(name = "teams_address", length = 1000)
    private String teamsAddress;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "updated_by")
    private Long updatedBy;
    @PrePersist protected void onCreate(){OffsetDateTime n=OffsetDateTime.now();if(createdAt==null)createdAt=n;if(updatedAt==null)updatedAt=n;}
    @PreUpdate protected void onUpdate(){updatedAt=OffsetDateTime.now();}
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getPhoneNumber(){return phoneNumber;} public void setPhoneNumber(String v){phoneNumber=v;}
    public String getTeamsAddress(){return teamsAddress;} public void setTeamsAddress(String v){teamsAddress=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public OffsetDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(OffsetDateTime v){createdAt=v;}
    public OffsetDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(OffsetDateTime v){updatedAt=v;}
    public Long getCreatedBy(){return createdBy;} public void setCreatedBy(Long v){createdBy=v;}
    public Long getUpdatedBy(){return updatedBy;} public void setUpdatedBy(Long v){updatedBy=v;}
}
