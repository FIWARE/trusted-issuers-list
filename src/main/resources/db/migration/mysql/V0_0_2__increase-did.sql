ALTER TABLE `trusted_issuer` MODIFY COLUMN `did` varchar(1024) NOT NULL;
ALTER TABLE `credential` MODIFY COLUMN `trusted_issuer_id` varchar(1024) NOT NULL;