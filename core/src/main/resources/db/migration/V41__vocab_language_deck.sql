ALTER TABLE vocab_card ADD COLUMN language VARCHAR(16);
ALTER TABLE vocab_card ADD COLUMN deck VARCHAR(80);

UPDATE vocab_card
SET language = CASE
    WHEN front ~ '[㐀-鿿]' THEN 'CHINESE'
    ELSE 'ENGLISH'
END;

ALTER TABLE vocab_card ALTER COLUMN language SET NOT NULL;
ALTER TABLE vocab_card ADD CONSTRAINT vocab_card_language_check
    CHECK (language IN ('ENGLISH', 'CHINESE'));

CREATE INDEX idx_vocab_card_language_active
    ON vocab_card (language, suspended);
