-- QC Routing: maps worker roles to the manager roles that receive their QC submissions.
-- When a worker submits a task, only managers whose role is mapped to the worker's role
-- will see it in their QC review queue.  Roles with no mapping default to all managers.
CREATE TABLE IF NOT EXISTS qc_routing (
    id              INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    worker_role_id  VARCHAR(20)  NOT NULL,
    manager_role_id VARCHAR(20)  NOT NULL,
    UNIQUE KEY uq_qc_routing (worker_role_id, manager_role_id),
    CONSTRAINT fk_qcr_worker  FOREIGN KEY (worker_role_id)  REFERENCES roles(role_id),
    CONSTRAINT fk_qcr_manager FOREIGN KEY (manager_role_id) REFERENCES roles(role_id)
);
