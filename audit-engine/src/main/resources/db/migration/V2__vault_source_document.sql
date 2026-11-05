-- Record which file each audit was run on, with a SHA-256 fingerprint of its exact bytes,
-- so anyone holding the original document can prove it is the one that was audited.
-- Adding nullable columns doesn't touch existing rows, so the append-only triggers allow it.
ALTER TABLE financial_audit_vault ADD COLUMN source_name VARCHAR(255);
ALTER TABLE financial_audit_vault ADD COLUMN source_sha256 VARCHAR(64);
