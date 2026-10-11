package com.dxc.monitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "menu_items", schema = "ra_fcb")
public class MenuItem
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_key", nullable = false, length = 80)
    private String menuKey;

    @Column(name = "menu_name", nullable = false, length = 100)
    private String menuName;

    @Column(length = 500)
    private String url;

    @Column(length = 80)
    private String icon;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "required_permission", length = 100)
    private String requiredPermission;

    @Column(nullable = false)
    private boolean enabled = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMenuKey() { return menuKey; }
    public void setMenuKey(String menuKey) { this.menuKey = menuKey; }
    public String getMenuName() { return menuName; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public String getRequiredPermission() { return requiredPermission; }
    public void setRequiredPermission(String requiredPermission) { this.requiredPermission = requiredPermission; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
