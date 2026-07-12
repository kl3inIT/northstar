alter table ai_gateway_setting
    add column type varchar(40) not null default 'OPENAI_CHAT_COMPATIBLE';
