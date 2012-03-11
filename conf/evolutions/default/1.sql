# --- First database schema for PostgreSQL 9.1

# --- !Ups

CREATE TABLE "user"
(
   id SERIAL,
   name varchar(150) not null,
   githubId  bigint,
   disabled boolean not null default false,
   admin boolean not null default false,
   created timestamp not null default now(),
   lastAccess timestamp not null default now(),
   avatar varchar(250) not null default 'http://www.gravatar.com/avatar/00000000000000000000000000000000',
   location varchar(250),
   blog varchar(250),
   githubUrl varchar(250),
   bio TEXT,
   CONSTRAINT pk_user PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_user_githubid on "user" (githubId);
create index ix_user_disabled on "user" (disabled);

# --- !Downs

DROP TABLE if exists "user";


