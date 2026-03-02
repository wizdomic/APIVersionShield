CREATE TABLE IF NOT EXISTS api_contracts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    version    VARCHAR(255) NOT NULL UNIQUE,
    schema     CLOB         NOT NULL
);

CREATE TABLE IF NOT EXISTS guard_audit_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_version VARCHAR(255)  NOT NULL,
    to_version   VARCHAR(255)  NOT NULL,
    decision     VARCHAR(50)   NOT NULL,
    reason       VARCHAR(1000) NOT NULL,
    checked_at   TIMESTAMP     NOT NULL
);