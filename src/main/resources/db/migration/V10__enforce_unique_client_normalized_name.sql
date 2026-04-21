WITH ranked_clients AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, normalized_name
            ORDER BY created_at, id
        ) AS rn
    FROM clients
)
UPDATE clients c
SET normalized_name = LEFT(c.normalized_name, 450) || '__legacy__' || c.id
FROM ranked_clients rc
WHERE c.id = rc.id
  AND rc.rn > 1;

DROP INDEX IF EXISTS idx_clients_user_normalized_name;

ALTER TABLE clients
    ADD CONSTRAINT uk_clients_user_normalized_name UNIQUE (user_id, normalized_name);
