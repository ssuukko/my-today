ALTER TABLE app_user ADD COLUMN guest_expires_at TIMESTAMPTZ;
CREATE INDEX app_user_guest_expiry ON app_user(guest_expires_at) WHERE guest_expires_at IS NOT NULL;
