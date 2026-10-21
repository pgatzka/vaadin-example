alter table boards add column share_token varchar(40);
create unique index ix_boards_share_token on boards (share_token);
