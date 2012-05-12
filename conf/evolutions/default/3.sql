# --- Introducing Module entities

# --- !Ups

CREATE TABLE plugin (
    id SERIAL,
    name varchar(150) not null,
    version bigint references version,
    author bigint references publisher,
    url varchar(250) not null,
    description TEXT,
    positive int not null default 1,
    negative int not null default 0,
    created timestamp not null default now(),
    CONSTRAINT pk_plugin PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_plugin_name on plugin (name);
create index ix_plugin_version on plugin (version);
create index ix_plugin_author on plugin (author);
create index ix_plugin_positive on plugin (positive);
create index ix_plugin_negative on plugin (negative);

CREATE TABLE voteplugin (
    id SERIAL,
    plugin bigint references plugin,
    author bigint references publisher,
    vote int not null,
    created timestamp not null default now(),
    CONSTRAINT pk_voteplugin PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_voteplugin_author on voteplugin (author);
create index ix_voteplugin_plugin on voteplugin (plugin);


CREATE TABLE tagplugin (
    id SERIAL,
    plugin bigint references plugin,
    tag bigint references tag,
    created timestamp not null default now(),
    CONSTRAINT pk_tagplugin PRIMARY KEY (id)
)
WITH (
  OIDS = FALSE
);

create index ix_tagplugin_tag on tagplugin (tag);
create index ix_tagplugin_plugin on tagplugin (plugin);


# --- !Downs

DROP TABLE if exists tagplugin;
DROP TABLE if exists voteplugin;
DROP TABLE if exists plugin;


