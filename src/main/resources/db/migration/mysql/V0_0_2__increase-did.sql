ALTER TABLE `trusted_issuer` MODIFY COLUMN `did` varchar(768) NOT NULL;
ALTER TABLE `credential` MODIFY COLUMN `trusted_issuer_id` varchar(768) NOT NULL;