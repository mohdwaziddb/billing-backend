create table if not exists whatsapp_provider_settings (
    id bigint not null auto_increment,
    company_id bigint not null,
    provider_name varchar(255) not null,
    provider_type varchar(64) not null,
    auth_key text null,
    whatsapp_number varchar(64) not null,
    sender_name varchar(255) null,
    api_url text not null,
    is_active bit not null default 0,
    created_at datetime(6) null,
    created_by varchar(255) null,
    updated_at datetime(6) null,
    updated_by varchar(255) null,
    primary key (id),
    constraint fk_whatsapp_provider_company foreign key (company_id) references company (id)
);

create index idx_whatsapp_provider_company on whatsapp_provider_settings (company_id);
