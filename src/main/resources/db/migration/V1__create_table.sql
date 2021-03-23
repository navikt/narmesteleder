CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

create table narmeste_leder
(
    narmeste_leder_id            uuid DEFAULT uuid_generate_v4 () primary key,
    orgnummer                    VARCHAR                    not null,
    bruker_fnr                   VARCHAR                    not null,
    narmeste_leder_fnr           VARCHAR                    not null,
    narmeste_leder_telefonnummer VARCHAR                    not null,
    narmeste_leder_epost         VARCHAR                    not null,
    arbeidsgiver_forskutterer    BOOLEAN,
    aktiv_fom                    TIMESTAMP with time zone   not null,
    aktiv_tom                    TIMESTAMP with time zone,
    timestamp                    TIMESTAMP with time zone   not null
);

create index narmeste_leder_fnr_idx on narmeste_leder (bruker_fnr);
create index narmeste_leder_nlfnr_idx on narmeste_leder (narmeste_leder_fnr);
