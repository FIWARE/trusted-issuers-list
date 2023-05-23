CREATE TABLE IF NOT EXISTS `trusted_issuer` (
    `did` varchar(255) NOT NULL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS `capability` (
    `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `valid_from` date,
    `valid_to` date,
    `credentials_type` varchar(255) NOT NULL,
    `trusted_issuer_id` varchar(255) NOT NULL,
    CONSTRAINT `fk_trusted_issuer` FOREIGN KEY (`trusted_issuer_id`) REFERENCES `trusted_issuer` (`did`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `claim` (
    `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `name` varchar(255) NOT NULL,
    `capability_id` int NOT NULL,
    CONSTRAINT `fk_capability` FOREIGN KEY (`capability_id`) REFERENCES `capability` (`id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `claim_value` (
    `id` int NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `value` varchar(255) NOT NULL,
    `claim_id` int NOT NULL,
    CONSTRAINT `fk_claim` FOREIGN KEY (`claim_id`) REFERENCES `claim` (`id`) ON DELETE CASCADE
);