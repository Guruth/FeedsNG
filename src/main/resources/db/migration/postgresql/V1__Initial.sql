CREATE SCHEMA IF NOT EXISTS FEEDSNG;

-- Feed
CREATE TABLE FEEDSNG.feed
(
    id           SERIAL PRIMARY KEY,
    name         VARCHAR(256),
    description  VARCHAR(2048),
    feed_url     VARCHAR(2048) UNIQUE,
    site_url     VARCHAR(2048),
    last_updated TIMESTAMP WITH TIME ZONE
);
CREATE TABLE FEEDSNG.feed_item
(
    id       SERIAL PRIMARY KEY,
    feed_id  INTEGER,
    title    VARCHAR(2048),
    author   VARCHAR(256),
    html     TEXT,
    item_url VARCHAR(2048),
    created  TIMESTAMP WITH TIME ZONE,
    UNIQUE (feed_id, item_url)
);
CREATE TABLE FEEDSNG.user_group
(
    id      SERIAL PRIMARY KEY,
    user_id INTEGER,
    name    VARCHAR(256)
);
CREATE TABLE FEEDSNG.user_group_feed
(
    group_id INTEGER,
    feed_id  INTEGER
);
CREATE TABLE FEEDSNG.user_feed
(
    user_id INTEGER,
    feed_id INTEGER
);
CREATE TABLE FEEDSNG.user_feed_item
(
    feed_item_id INTEGER,
    user_id      INTEGER,
    saved        BOOLEAN DEFAULT FALSE,
    read         BOOLEAN DEFAULT FALSE,
    UNIQUE (feed_item_id, user_id)
);

-- User
CREATE TABLE FEEDSNG.account
(
    id                 SERIAL PRIMARY KEY,
    username           VARCHAR(256) UNIQUE,
    password_hash      VARCHAR(256),
    fever_api_key_hash VARCHAR(2048)
);
CREATE TABLE FEEDSNG.invite_code
(
    issued_by   INTEGER,
    invite_code VARCHAR(256) UNIQUE,
    used_by     INTEGER
);