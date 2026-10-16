-- target: tenant
-- Company branding columns now store FULL file JSON (file_name, type, url, key,
-- size, bucket, tenant, kind, uploaded_at) in the SAME logo_url / signature_url
-- columns. varchar(255/500) is too small for the JSON payload, so widen to TEXT.
-- Old plain values (/uploads/... paths, absolute URLs) keep working: backend
-- readers parse leniently, and the next upload rewrites them as JSON.
ALTER TABLE companies MODIFY COLUMN logo_url TEXT NULL;
ALTER TABLE companies MODIFY COLUMN signature_url TEXT NULL;
