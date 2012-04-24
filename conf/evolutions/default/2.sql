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

insert into version (name) values ('1.2');
insert into version (name) values ('2.0 Java');
insert into version (name) values ('2.0 Scala');


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

insert into tag (name) values ('authentication');
insert into tag (name) values ('websockets');


CREATE TABLE demo (
    id SERIAL,
    name varchar(150) not null,
    version bigint references version,
    author bigint references publisher,
    codeurl varchar(250) not null,
    demourl varchar(250),
    description TEXT,
    positive int not null default 1,
    negative int not null default 0,
    created timestamp not null default now(),
    CONSTRAINT pk_demo PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_demo_name on demo (name);
create index ix_demo_version on demo (version);
create index ix_demo_author on demo (author);
create index ix_demo_positive on demo (positive);
create index ix_demo_negative on demo (negative);

CREATE TABLE votedemo (
    id SERIAL,
    demo bigint references demo,
    author bigint references publisher,
    vote int not null,
    created timestamp not null default now(),
    CONSTRAINT pk_votedemo PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_votedemo_author on votedemo (author);
create index ix_votedemo_demo on votedemo (demo);


CREATE TABLE tagdemo (
    id SERIAL,
    demo bigint references demo,
    tag bigint references tag,
    created timestamp not null default now(),
    CONSTRAINT pk_tagdemo PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_tagdemo_tag on tagdemo (tag);
create index ix_tagdemo_demo on tagdemo (demo);


# --- !Downs

DROP TABLE if exists tagdemo;
DROP TABLE if exists votedemo;
DROP TABLE if exists demo;
DROP TABLE if exists version;
DROP TABLE if exists tag;


