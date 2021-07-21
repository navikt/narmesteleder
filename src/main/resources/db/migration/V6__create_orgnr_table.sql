CREATE TABLE orgnummer
(
    orgnummer VARCHAR primary key not null,
    juridisk_orgnummer varchar not null
);
create index juridisk_orgnummer_idx on orgnummer (juridisk_orgnummer);
