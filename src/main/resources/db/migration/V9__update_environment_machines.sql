ALTER TABLE ra_fcb.machines
    ADD COLUMN environment_id BIGINT;

ALTER TABLE ra_fcb.machines
    ADD CONSTRAINT fk_machines_environment
    FOREIGN KEY (environment_id)
    REFERENCES ra_fcb.environments(id);
    
    