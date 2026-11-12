-- Record which signed-in user ran each audit. Records vaulted before this migration keep NULL.
-- Adding a nullable column doesn't touch existing rows, so the append-only triggers allow it.
ALTER TABLE financial_audit_vault ADD COLUMN uploaded_by VARCHAR(255);
