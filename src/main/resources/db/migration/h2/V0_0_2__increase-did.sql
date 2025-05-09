ALTER TABLE `trusted_issuer` ALTER COLUMN `did` varchar(1024) NOT NULL;
ALTER TABLE `credential` ALTER COLUMN `trusted_issuer_id` varchar(1024) NOT NULL;