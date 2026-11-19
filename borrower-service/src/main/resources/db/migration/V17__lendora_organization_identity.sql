ALTER TABLE organization ADD COLUMN lendora_external_key VARCHAR(80);
CREATE UNIQUE INDEX ux_organization_lendora_key ON organization(lendora_external_key);
