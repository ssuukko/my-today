CREATE TABLE activity_log (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
 category VARCHAR(60) NOT NULL CHECK (length(trim(category)) > 0),
 activity_date DATE NOT NULL,
 duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 0 AND 1440),
 quantity NUMERIC(20,6) CHECK (quantity > 0),
 unit VARCHAR(30),
 notes VARCHAR(5000),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at TIMESTAMPTZ,
 CHECK ((quantity IS NULL AND unit IS NULL) OR (quantity IS NOT NULL AND unit IS NOT NULL AND length(trim(unit)) > 0)),
 CHECK (duration_minutes > 0 OR quantity IS NOT NULL)
);
CREATE INDEX activity_owner_date ON activity_log(user_id,activity_date,created_at,id) WHERE deleted_at IS NULL;

CREATE TABLE growth_goal (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
 category VARCHAR(60) NOT NULL CHECK (length(trim(category)) > 0),
 start_date DATE NOT NULL,
 end_date DATE NOT NULL,
 target_minutes INTEGER NOT NULL CHECK (target_minutes BETWEEN 1 AND 525600),
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 deleted_at TIMESTAMPTZ,
 CHECK (end_date >= start_date AND end_date - start_date < 366)
);
CREATE INDEX growth_goal_owner ON growth_goal(user_id,created_at,id) WHERE deleted_at IS NULL;

CREATE TABLE growth_routine (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES app_user(id),
 title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
 weekdays VARCHAR(13) NOT NULL,
 start_date DATE NOT NULL,
 target_minutes INTEGER NOT NULL CHECK (target_minutes BETWEEN 1 AND 1440),
 active BOOLEAN NOT NULL DEFAULT true,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 archived_date DATE,
 UNIQUE (id,user_id)
);
CREATE INDEX growth_routine_owner ON growth_routine(user_id,created_at,id);

CREATE TABLE growth_routine_revision (
 routine_id UUID NOT NULL,
 user_id UUID NOT NULL,
 version BIGINT NOT NULL,
 effective_from DATE NOT NULL,
 title VARCHAR(200) NOT NULL,
 weekdays VARCHAR(13) NOT NULL,
 target_minutes INTEGER NOT NULL CHECK (target_minutes BETWEEN 1 AND 1440),
 active BOOLEAN NOT NULL,
 PRIMARY KEY(routine_id,version),
 FOREIGN KEY(routine_id,user_id) REFERENCES growth_routine(id,user_id)
);

CREATE TABLE growth_routine_occurrence (
 routine_id UUID NOT NULL,
 user_id UUID NOT NULL,
 scheduled_date DATE NOT NULL,
 title VARCHAR(200) NOT NULL,
 target_minutes INTEGER NOT NULL CHECK (target_minutes BETWEEN 1 AND 1440),
 status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DONE','SKIPPED')),
 version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(routine_id,scheduled_date),
 FOREIGN KEY(routine_id,user_id) REFERENCES growth_routine(id,user_id)
);
CREATE INDEX growth_occurrence_owner_date ON growth_routine_occurrence(user_id,scheduled_date);

CREATE TABLE growth_reflection (
 user_id UUID NOT NULL REFERENCES app_user(id),
 date DATE NOT NULL,
 well VARCHAR(2000) NOT NULL DEFAULT '',
 missed VARCHAR(2000) NOT NULL DEFAULT '',
 next VARCHAR(2000) NOT NULL DEFAULT '',
 version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,date)
);
