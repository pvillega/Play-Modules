# --- First database schema for PostgreSQL 9.1

# --- !Ups

CREATE TABLE publisher
(
   id SERIAL,
   name varchar(150) not null,
   githubId  bigint,
   twitterId bigint,
   googleId varchar(250),
   disabled boolean not null default false,
   admin boolean not null default false,
   created timestamp not null default now(),
   lastAccess timestamp not null default now(),
   avatar varchar(250) not null default 'http://www.gravatar.com/avatar/00000000000000000000000000000000?s=160',
   url varchar(250),
   location varchar(250),
   bio TEXT,
   CONSTRAINT pk_user PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_user_githubid on publisher (githubId);
create index ix_user_twitterid on publisher (twitterId);
create index ix_user_googleid on publisher (googleId);
create index ix_user_disabled on publisher (disabled);

# --- !Downs

DROP TABLE if exists publisher;


