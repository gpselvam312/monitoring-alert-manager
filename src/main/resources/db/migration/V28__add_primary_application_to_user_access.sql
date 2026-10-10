-- Each user/application access assignment may mark one application as the user's dashboard default.
ALTER TABLE ra_fcb.user_application_roles
    ADD COLUMN is_primary BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve a deterministic default for existing users: their lowest application ID.
WITH ranked_assignments AS (
    SELECT user_id, application_id,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY application_id) AS row_number
    FROM ra_fcb.user_application_roles
)
UPDATE ra_fcb.user_application_roles assignment
SET is_primary = (ranked_assignments.row_number = 1)
FROM ranked_assignments
WHERE assignment.user_id = ranked_assignments.user_id
  AND assignment.application_id = ranked_assignments.application_id;

CREATE UNIQUE INDEX uq_user_application_roles_one_primary_per_user
    ON ra_fcb.user_application_roles(user_id)
    WHERE is_primary = TRUE;
