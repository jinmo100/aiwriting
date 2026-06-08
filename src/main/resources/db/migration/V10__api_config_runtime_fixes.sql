-- Runtime compatibility fix for databases that applied an older V1 where
-- api_configs.api_key was still NOT NULL. New writes store only encrypted keys.
ALTER TABLE api_configs ALTER COLUMN api_key DROP NOT NULL;
