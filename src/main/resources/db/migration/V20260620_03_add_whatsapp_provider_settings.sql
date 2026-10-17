create table if not exists whatsapp_provider_settings (
    id bigint not null auto_increment,
    provider_name varchar(255) not null,
    provider_type enum('MSG91','PINNACLE') not null,
    auth_key varchar(255) null,
    whatsapp_number varchar(255) not null,
    sender_name varchar(255) null,
    api_url text not null,
    provider_config longtext null,
    is_active bit not null default 0,
    created_at datetime(6) not null,
    created_by varchar(255) null,
    updated_at datetime(6) not null,
    updated_by varchar(255) null,
    primary key (id)
);
