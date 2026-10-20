-- Run with psql -v ON_ERROR_STOP=1 after Flyway V2 has been applied.
-- All fixture data is rolled back, including on connection close after failure.
BEGIN;

DO $$
DECLARE
    owner_id UUID := gen_random_uuid();
    other_owner_id UUID := gen_random_uuid();
    category_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO users (id, email, password_hash) VALUES
        (owner_id, owner_id::text || '@example.invalid', 'test-only-placeholder'),
        (other_owner_id, other_owner_id::text || '@example.invalid', 'test-only-placeholder');

    INSERT INTO categories (id, user_id, name, type)
    VALUES (category_id, owner_id, 'verification', 'EXPENSE');

    IF NOT EXISTS (
        SELECT 1 FROM categories
        WHERE id = category_id AND archived = FALSE AND created_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Default values verification failed';
    END IF;

    -- The same name is allowed for another owner or another type.
    INSERT INTO categories (id, user_id, name, type) VALUES
        (gen_random_uuid(), other_owner_id, 'verification', 'EXPENSE'),
        (gen_random_uuid(), owner_id, 'verification', 'INCOME'),
        (gen_random_uuid(), owner_id, 'Verification', 'EXPENSE');

    -- Archiving does not release the unique name.
    UPDATE categories SET archived = TRUE WHERE id = category_id;
    BEGIN
        INSERT INTO categories (id, user_id, name, type)
        VALUES (gen_random_uuid(), owner_id, 'verification', 'EXPENSE');
        RAISE EXCEPTION 'Duplicate category was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO categories (id, user_id, name, type)
        VALUES (gen_random_uuid(), owner_id, 'invalid-type', 'OTHER');
        RAISE EXCEPTION 'Invalid type was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO categories (id, user_id, name, type)
        VALUES (gen_random_uuid(), owner_id, '   ', 'EXPENSE');
        RAISE EXCEPTION 'Blank name was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO categories (id, user_id, name, type)
        VALUES (gen_random_uuid(), gen_random_uuid(), 'missing-owner', 'EXPENSE');
        RAISE EXCEPTION 'Missing owner was accepted';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    BEGIN
        DELETE FROM users WHERE id = owner_id;
        RAISE EXCEPTION 'Owner with categories was deleted';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    RAISE NOTICE 'Category constraints verification passed';
END;
$$;

ROLLBACK;
