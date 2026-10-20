-- Run with psql -v ON_ERROR_STOP=1 after Flyway V3 has been applied.
-- This script checks database constraints, not API authorization or locking.
BEGIN;

DO $$
DECLARE
    owner_id UUID := gen_random_uuid();
    other_owner_id UUID := gen_random_uuid();
    category_id UUID := gen_random_uuid();
    transaction_id UUID := gen_random_uuid();
    invalid_amount NUMERIC;
    invalid_description TEXT;
    statement TEXT;
    expected_state TEXT;
    actual_state TEXT;
    failure_case RECORD;
BEGIN
    INSERT INTO users (id, email, password_hash) VALUES
        (owner_id, owner_id::text || '@example.invalid', 'test-only-placeholder'),
        (other_owner_id, other_owner_id::text || '@example.invalid', 'test-only-placeholder');
    INSERT INTO categories (id, user_id, name, type)
    VALUES (category_id, owner_id, 'verification', 'EXPENSE');

    INSERT INTO transactions (id, user_id, category_id, type, amount, occurred_on, description)
    VALUES (transaction_id, owner_id, category_id, 'EXPENSE', 1, DATE '2026-10-20', 'minimum');
    IF NOT EXISTS (
        SELECT 1 FROM transactions WHERE id = transaction_id
        AND amount = 1 AND occurred_on = DATE '2026-10-20'
        AND memo = '' AND version = 0
        AND created_at IS NOT NULL AND updated_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Transaction defaults verification failed';
    END IF;

    UPDATE transactions SET amount = 999999999999999, description = repeat('x', 100),
        memo = repeat('x', 1000) WHERE id = transaction_id;

    FOREACH invalid_amount IN ARRAY ARRAY[0::numeric, -1::numeric, 'NaN'::numeric] LOOP
        BEGIN
            UPDATE transactions SET amount = invalid_amount WHERE id = transaction_id;
            RAISE EXCEPTION 'Invalid amount was accepted: %', invalid_amount;
        EXCEPTION WHEN check_violation THEN
            NULL;
        END;
    END LOOP;

    FOREACH invalid_description IN ARRAY ARRAY['', '   '] LOOP
        BEGIN
            UPDATE transactions SET description = invalid_description WHERE id = transaction_id;
            RAISE EXCEPTION 'Blank description was accepted';
        EXCEPTION WHEN check_violation THEN
            NULL;
        END;
    END LOOP;

    -- Exact SQLSTATE checks ensure each invalid operation fails as expected.
    FOR failure_case IN
        SELECT * FROM (VALUES
            (format('UPDATE transactions SET amount = 1000000000000000 WHERE id = %L', transaction_id), '22003'),
            (format('UPDATE transactions SET amount = NULL WHERE id = %L', transaction_id), '23502'),
            (format('UPDATE transactions SET occurred_on = NULL WHERE id = %L', transaction_id), '23502'),
            (format('UPDATE transactions SET category_id = NULL WHERE id = %L', transaction_id), '23502'),
            (format('UPDATE transactions SET description = %L WHERE id = %L', repeat('x', 101), transaction_id), '22001'),
            (format('UPDATE transactions SET memo = %L WHERE id = %L', repeat('x', 1001), transaction_id), '22001'),
            (format('UPDATE transactions SET version = -1 WHERE id = %L', transaction_id), '23514'),
            (format('UPDATE transactions SET type = %L WHERE id = %L', 'OTHER', transaction_id), '23514'),
            (format('UPDATE transactions SET user_id = %L WHERE id = %L', other_owner_id, transaction_id), '23503'),
            (format('UPDATE transactions SET type = %L WHERE id = %L', 'INCOME', transaction_id), '23503'),
            (format('UPDATE transactions SET category_id = %L WHERE id = %L', gen_random_uuid(), transaction_id), '23503'),
            (format('DELETE FROM categories WHERE id = %L', category_id), '23503'),
            (format('UPDATE categories SET user_id = %L WHERE id = %L', other_owner_id, category_id), '23503'),
            (format('UPDATE categories SET type = %L WHERE id = %L', 'INCOME', category_id), '23503'),
            (format('DELETE FROM users WHERE id = %L', owner_id), '23503')
        ) AS cases(sql_text, sql_state)
    LOOP
        statement := failure_case.sql_text;
        expected_state := failure_case.sql_state;
        actual_state := NULL;
        BEGIN
            EXECUTE statement;
        EXCEPTION WHEN OTHERS THEN
            GET STACKED DIAGNOSTICS actual_state = RETURNED_SQLSTATE;
        END;
        IF actual_state IS DISTINCT FROM expected_state THEN
            RAISE EXCEPTION 'Expected SQLSTATE %, got % for %', expected_state, actual_state, statement;
        END IF;
    END LOOP;

    -- Renaming/archiving preserves existing references and permits memo/amount edits.
    UPDATE categories SET name = 'renamed', archived = TRUE WHERE id = category_id;
    UPDATE transactions SET amount = 12000, memo = 'existing transaction'
    WHERE id = transaction_id;
    IF NOT EXISTS (
        SELECT 1 FROM transactions t JOIN categories c ON c.id = t.category_id
        WHERE t.id = transaction_id AND t.amount = 12000
        AND c.name = 'renamed' AND c.archived = TRUE
    ) THEN
        RAISE EXCEPTION 'Archived category reference verification failed';
    END IF;

    RAISE NOTICE 'Transaction constraints verification passed';
END;
$$;

ROLLBACK;
