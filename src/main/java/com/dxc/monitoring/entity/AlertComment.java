package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_comments", schema = "ra_fcb")
public class AlertComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_id", nullable = false)
    private Alert alert;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "author_name", nullable = false, length = 200)
    private String authorName;

    @Column(name = "comment_text", nullable = false, columnDefinition = "text")
    private String commentText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Alert getAlert() { return alert; }
    public void setAlert(Alert alert) { this.alert = alert; }
    public Long getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String commentText) { this.commentText = commentText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
