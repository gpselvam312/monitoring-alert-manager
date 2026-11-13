-- Preserve legacy per-rule message templates before they are retired.
-- This is conditional because some environments may already have applied V57.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'ra_fcb'
          AND table_name = 'alert_rules'
          AND column_name = 'alert_message'
    ) THEN
        EXECUTE $migration$
            UPDATE ra_fcb.alert_rules
            SET presentation_config = jsonb_build_object(
                'version', 1,
                'sections', jsonb_build_array(
                    jsonb_build_object(
                        'id', 'legacy-message',
                        'type', 'TEXT',
                        'title', 'Alert details',
                        'text', alert_message
                    )
                )
            )
            WHERE COALESCE(presentation_config, '{}'::jsonb) = '{}'::jsonb
              AND alert_message IS NOT NULL
              AND btrim(alert_message) <> ''
        $migration$;
    END IF;
END
$$;
