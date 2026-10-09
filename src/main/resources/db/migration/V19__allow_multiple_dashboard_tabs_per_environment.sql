-- A dashboard environment can have multiple tabs (for example, Batch Jobs,
-- APIs, and Infrastructure). Tab names remain unique, but environment_id does not.
DROP INDEX IF EXISTS ra_fcb.uk_dashboard_tabs_environment_id;
