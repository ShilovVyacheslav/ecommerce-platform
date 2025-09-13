create table users (
    id bigserial primary key,
    fullname varchar(64) not null,
    username varchar(32) not null unique,
    email varchar(64) not null unique,
    password varchar(256) not null,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null
);