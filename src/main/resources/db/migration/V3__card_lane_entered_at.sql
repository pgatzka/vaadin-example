-- When a card entered its current lane, for card aging and time-in-lane metrics.
alter table cards add column lane_entered_at timestamp(6) with time zone;
update cards set lane_entered_at = updated_at;
alter table cards alter column lane_entered_at set not null;
