-- Append-only collaboration comments for alert lifecycles.
-- Alert identity remains based on the immutable alert row and is rendered as ALT-yyyy-########.
CREATE TABLE ra_fcb.alert_comments (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    author_user_id BIGINT,
    comment_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_comments_alert
        FOREIGN KEY (alert_id) REFERENCES ra_fcb.alerts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_alert_comments_author
        FOREIGN KEY (author_user_id) REFERENCES ra_fcb.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_alert_comments_text
        CHECK (length(btrim(comment_text)) BETWEEN 1 AND 5000)
);
CREATE INDEX idx_alert_comments_alert_created
    ON ra_fcb.alert_comments (alert_id, created_at DESC, id DESC);
