# --- Introducing new entities

# --- !Ups

CREATE TABLE version
(
   id SERIAL,
   name varchar(15) not null,
   parent int,
   created timestamp not null default now(),
   CONSTRAINT pk_version PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_version_parent on version (parent);

CREATE TABLE tag
(
   id SERIAL,
   name varchar(150) not null,
   CONSTRAINT pk_tag PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_tag_name on tag(name);

# --- !Downs

DROP TABLE if exists version;
DROP TABLE if exists tag;


