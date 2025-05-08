ALTER TABLE `trusted_issuer` ${modify_column} COLUMN `did` varchar(1024) NOT NULL;
ALTER TABLE `credential` ${modify_column} COLUMN `trusted_issuer_id` varchar(1024) NOT NULL;