ALTER TABLE categories
    ADD CONSTRAINT uk_categories_id_user_type UNIQUE (id, user_id, type);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category_id UUID NOT NULL,
    type VARCHAR(7) NOT NULL,
    amount NUMERIC(15,0) NOT NULL,
    occurred_on DATE NOT NULL,
    description VARCHAR(100) NOT NULL,
    memo VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_transactions_category_owner_type
        FOREIGN KEY (category_id, user_id, type)
        REFERENCES categories (id, user_id, type)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount CHECK (amount BETWEEN 1 AND 999999999999999),
    CONSTRAINT ck_transactions_description_not_blank CHECK (length(btrim(description)) > 0),
    CONSTRAINT ck_transactions_version CHECK (version >= 0)
);

CREATE INDEX idx_transactions_user_occurred_id
    ON transactions (user_id, occurred_on DESC, id DESC);

CREATE INDEX idx_transactions_category_owner_type
    ON transactions (category_id, user_id, type);
