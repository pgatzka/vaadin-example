-- "Blocked by" links between cards of the same board.
create table card_blockers (
    card_id    bigint not null,
    blocker_id bigint not null,
    primary key (card_id, blocker_id),
    constraint fk_card_blockers_card foreign key (card_id) references cards (id),
    constraint fk_card_blockers_blocker foreign key (blocker_id) references cards (id)
);

create index ix_card_blockers_blocker on card_blockers (blocker_id);
