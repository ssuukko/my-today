CREATE TABLE task (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
 status VARCHAR(20) NOT NULL DEFAULT 'TODO' CHECK (status IN ('TODO','IN_PROGRESS','DONE','CANCELED')),
 priority VARCHAR(2) NOT NULL DEFAULT 'P2' CHECK (priority IN ('P0','P1','P2','P3')),
 due_date DATE,
 due_at TIMESTAMPTZ,
 estimated_minutes INTEGER CHECK (estimated_minutes BETWEEN 1 AND 10080),
 completed_at TIMESTAMPTZ,
 status_changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK (due_date IS NULL OR due_at IS NULL),
 CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);
CREATE INDEX task_owner_active ON task(user_id, created_at, id) WHERE deleted_at IS NULL;
CREATE INDEX task_owner_due_date ON task(user_id, due_date) WHERE deleted_at IS NULL;
CREATE INDEX task_owner_due_at ON task(user_id, due_at) WHERE deleted_at IS NULL;

CREATE TABLE calendar_event (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
 all_day BOOLEAN NOT NULL,
 start_at TIMESTAMPTZ,
 end_at TIMESTAMPTZ,
 start_date DATE,
 end_date_exclusive DATE,
 timezone VARCHAR(64) NOT NULL,
 location VARCHAR(300),
 notes VARCHAR(5000),
 source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL' CHECK (source = 'INTERNAL'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0,
 CHECK ((all_day AND start_date IS NOT NULL AND end_date_exclusive IS NOT NULL AND end_date_exclusive > start_date AND start_at IS NULL AND end_at IS NULL)
     OR (NOT all_day AND start_at IS NOT NULL AND end_at IS NOT NULL AND end_at > start_at AND start_date IS NULL AND end_date_exclusive IS NULL))
);
CREATE INDEX calendar_event_owner_active ON calendar_event(user_id, created_at, id) WHERE deleted_at IS NULL;
CREATE INDEX calendar_event_owner_start ON calendar_event(user_id, start_at) WHERE deleted_at IS NULL;
CREATE INDEX calendar_event_owner_date ON calendar_event(user_id, start_date) WHERE deleted_at IS NULL;
