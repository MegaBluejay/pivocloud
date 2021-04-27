drop table if exists users, marines;
drop type if exists cat, weapon, melee, chapter;

create table users (
    name text primary key,
    hash char(32) not null
);

create type cat as enum (
    'AGGRESSOR',
    'TACTICAL',
    'TERMINATOR'
);

create type weapon as enum (
    'BOLTGUN',
    'BOLT_PISTOL',
    'BOLT_RIFLE'
);

create type melee as enum (
    'CHAIN_AXE',
    'MANREAPER',
    'POWER_FIST'
);

create type chapter as (
    name text,
    world text
);

create table marines (
    id serial primary key,
    owner text references users(name),
    name text not null,
    coords point not null,
    date date not null,
    health real not null,
    category cat,
    weapon weapon not null,
    melee melee not null,
    chapter chapter check ( name(chapter) is not null )
);
