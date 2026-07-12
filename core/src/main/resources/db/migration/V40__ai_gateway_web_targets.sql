ALTER TABLE ai_gateway_setting
    ADD COLUMN web_search_targets TEXT NOT NULL DEFAULT '',
    ADD COLUMN web_fetch_targets TEXT NOT NULL DEFAULT '',
    ADD COLUMN stt_targets TEXT NOT NULL DEFAULT '',
    ADD COLUMN image_targets TEXT NOT NULL DEFAULT '',
    ADD COLUMN embedding_targets TEXT NOT NULL DEFAULT '';

ALTER TABLE ai_route_setting
    ADD COLUMN options JSONB NOT NULL DEFAULT '{}'::jsonb;
