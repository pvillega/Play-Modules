# --- Introducing Module releases

# --- !Ups

CREATE TABLE release (
    id SERIAL,
    name varchar(150) not null,
    plugin bigint references plugin ON DELETE CASCADE,
    description TEXT,
    created timestamp not null default now(),
    CONSTRAINT pk_release PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_release_plugin on release (plugin);

# --- !Downs

DROP TABLE if exists release;


