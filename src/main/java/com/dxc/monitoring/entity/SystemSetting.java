package com.dxc.monitoring.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "system_settings", schema = "ra_fcb")
public class SystemSetting
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    public Long getId()
    {
        return id;
    }

    public String getSettingKey()
    {
        return settingKey;
    }

    public void setSettingKey(String settingKey)
    {
        this.settingKey = settingKey;
    }

    public String getSettingValue()
    {
        return settingValue;
    }

    public void setSettingValue(String settingValue)
    {
        this.settingValue = settingValue;
    }

    public String getDataType()
    {
        return dataType;
    }

    public void setDataType(String dataType)
    {
        this.dataType = dataType;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt)
    {
        this.createdAt = createdAt;
    }

    public User getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    public User getUpdatedBy()
    {
        return updatedBy;
    }

    public void setUpdatedBy(User updatedBy)
    {
        this.updatedBy = updatedBy;
    }
}
