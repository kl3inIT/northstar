alter table web_research_setting
    add column search_gateway_id varchar(64),
    add column search_target_id varchar(200),
    add column page_gateway_id varchar(64),
    add column page_target_id varchar(200);
