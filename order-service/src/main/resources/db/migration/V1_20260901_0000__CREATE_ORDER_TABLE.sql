CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE orders
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    user_id        BIGINT         NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    total_amount   NUMERIC(12, 2) NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version        BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_user_id ON orders (user_id);