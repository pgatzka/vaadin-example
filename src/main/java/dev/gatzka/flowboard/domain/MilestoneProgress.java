package dev.gatzka.flowboard.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Where a milestone stands: how many of its cards are done, and, from the board's pace, how likely
 * the rest is to be finished by the target date if the team takes the milestone's cards first.
 *
 * @param forecast the simulation for the milestone's open cards with the target date as its goal
 */
public record MilestoneProgress(MilestoneSnapshot milestone, int done, int open, Forecast forecast) {

    public int total() {
        return done + open;
    }

    /** Share of the milestone's cards that are done, 0 to 1; a milestone without cards counts as untouched. */
    public double ratio() {
        return total() == 0 ? 0 : (double) done / total();
    }

    public boolean isComplete() {
        return total() > 0 && open == 0;
    }

    /** Chance, 0 to 1, of finishing the open cards by the target date; empty without a target, open work or history. */
    public Optional<Double> chance() {
        return forecast.targetChance();
    }

    /** The target has passed with cards still open. */
    public boolean isLate(LocalDate today) {
        return milestone.hasTarget() && open > 0 && milestone.targetDate().isBefore(today);
    }
}
