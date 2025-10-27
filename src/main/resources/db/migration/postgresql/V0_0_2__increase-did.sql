ALTER TABLE trusted_issuer ALTER COLUMN did TYPE varchar(768);
ALTER TABLE trusted_issuer ALTER COLUMN did SET NOT NULL;
ALTER TABLE credential ALTER COLUMN trusted_issuer_id TYPE varchar(768);
ALTER TABLE credential ALTER COLUMN trusted_issuer_id SET NOT NULL;