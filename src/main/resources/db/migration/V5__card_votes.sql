-- One vote per person per card, used to prioritise backlogs.
create table card_votes (
    card_id bigint      not null,
    voter   varchar(60) not null,
    primary key (card_id, voter),
    constraint fk_card_votes_card foreign key (card_id) references cards (id)
);
