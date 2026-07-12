ALTER TABLE speaking_feedback
    ADD COLUMN ielts_estimate VARCHAR(12000) NOT NULL
        DEFAULT '{"criteria":[],"overallMin":null,"overallMax":null,"confidence":"LOW","label":"Legacy attempt — estimate unavailable"}',
    ADD COLUMN estimate_version VARCHAR(64) NOT NULL DEFAULT 'legacy-unavailable';

ALTER TABLE speaking_feedback ALTER COLUMN ielts_estimate DROP DEFAULT;
ALTER TABLE speaking_feedback ALTER COLUMN estimate_version DROP DEFAULT;
