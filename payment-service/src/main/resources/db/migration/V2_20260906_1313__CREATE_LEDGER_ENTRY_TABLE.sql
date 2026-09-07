CREATE TABLE ledger_entries
(
    id             UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    transaction_id UUID           NOT NULL,
    payment_id     UUID           NOT NULL REFERENCES payments (id),
    account        VARCHAR(30)    NOT NULL,
    entry_type     VARCHAR(10)    NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_entries_payment_id ON ledger_entries (payment_id);
CREATE INDEX idx_ledger_entries_account ON ledger_entries (account);