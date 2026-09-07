CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE payments
(
    id                 UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    order_id           UUID           NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    provider_reference VARCHAR(255),
    failure_reason     VARCHAR(500),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT uq_payments_order_id UNIQUE (order_id)
);