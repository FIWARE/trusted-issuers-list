CREATE TABLE IF NOT EXISTS trusted_issuer (
    did varchar(255) NOT NULL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS credential (
    id SERIAL PRIMARY KEY,
    valid_from date,
    valid_to date,
    credentials_type varchar(255) NOT NULL,
    trusted_issuer_id varchar(255) NOT NULL,
    CONSTRAINT fk_trusted_issuer FOREIGN KEY (trusted_issuer_id) REFERENCES trusted_issuer (did) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS claim (
    id SERIAL PRIMARY KEY,
    name varchar(255) NOT NULL,
    credential_id int NOT NULL,
    CONSTRAINT fk_credential FOREIGN KEY (credential_id) REFERENCES credential (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS claim_value (
    id SERIAL PRIMARY KEY,
    value varchar(255) NOT NULL,
    claim_id int NOT NULL,
    CONSTRAINT fk_claim FOREIGN KEY (claim_id) REFERENCES claim (id) ON DELETE CASCADE
);