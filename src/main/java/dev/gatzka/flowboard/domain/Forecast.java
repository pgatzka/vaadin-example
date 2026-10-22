package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A Monte Carlo answer to "when will the open cards be done?".
 *
 * <p>The board's own history is the model: how many cards reached a done lane on each of the last
 * {@value #HISTORY_DAYS} days, quiet days included, because they are part of how the team actually
 * works. Every trial replays the remaining work by drawing random days from that history until each
 * card is done. Thousands of trials give dates with a probability attached instead of one guess:
 * the day by which half of the trials had finished, the day by which 85 percent had, and so on.
 *
 * @param today the day the forecast counts from
 * @param remaining open cards, the work still to do
 * @param throughput cards completed per day over the history window, oldest day first
 * @param target the latest due date among the open cards, or {@code null} without due dates
 * @param outcomes the dates for each confidence level in {@link #CONFIDENCES}; empty without a forecast
 * @param distribution how many trials finished on each day, from the earliest to the latest finish
 */
public record Forecast(LocalDate today, int remaining, List<Integer> throughput, LocalDate target,
        List<Outcome> outcomes, List<Bucket> distribution) {

    public static final int HISTORY_DAYS = 60;
    public static final int TRIALS = 5000;
    /** Fewer completions than this and the history says more about chance than about the team. */
    public static final int MIN_COMPLETIONS = 3;
    public static final List<Integer> CONFIDENCES = List.of(50, 85, 95);
    /** Guards a trial against running forever when the history is almost all quiet days. */
    static final int MAX_DAYS = 730;

    /** Why there is no forecast. */
    public enum Reason {
        NOTHING_OPEN, TOO_LITTLE_HISTORY
    }

    /** @param confidence percent of trials that had finished by {@code date}, {@code days} from today */
    public record Outcome(int confidence, LocalDate date, int days) {
    }

    /** @param trials how many of the {@link #TRIALS} finished exactly {@code days} after today */
    public record Bucket(int days, LocalDate date, int trials) {
    }

    public Forecast {
        requireNonNull(today);
        throughput = List.copyOf(throughput);
        outcomes = List.copyOf(outcomes);
        distribution = List.copyOf(distribution);
    }

    /**
     * Runs the simulation. The same inputs and {@code seed} always give the same forecast, so the
     * numbers do not flicker when a board is opened twice.
     */
    public static Forecast of(int remaining, List<Integer> throughput, LocalDate today, LocalDate target, long seed) {
        if (remaining <= 0 || throughput.isEmpty() || throughput.stream().mapToInt(Integer::intValue).sum() < MIN_COMPLETIONS) {
            return new Forecast(today, Math.max(0, remaining), throughput, target, List.of(), List.of());
        }
        int[] days = new int[TRIALS];
        Random random = new Random(seed);
        int history = throughput.size();
        for (int trial = 0; trial < TRIALS; trial++) {
            int done = 0;
            int day = 0;
            while (done < remaining && day < MAX_DAYS) {
                done += throughput.get(random.nextInt(history));
                day++;
            }
            days[trial] = day;
        }
        Arrays.sort(days);
        List<Outcome> outcomes = CONFIDENCES.stream()
                .map(confidence -> {
                    int finish = days[Math.max(0, (int) Math.ceil(confidence / 100.0 * TRIALS) - 1)];
                    return new Outcome(confidence, today.plusDays(finish), finish);
                })
                .toList();
        List<Bucket> distribution = new ArrayList<>();
        int first = days[0];
        int last = days[TRIALS - 1];
        int[] perDay = new int[last - first + 1];
        for (int day : days) {
            perDay[day - first]++;
        }
        for (int i = 0; i < perDay.length; i++) {
            distribution.add(new Bucket(first + i, today.plusDays(first + i), perDay[i]));
        }
        return new Forecast(today, remaining, throughput, target, outcomes, distribution);
    }

    public boolean hasForecast() {
        return !outcomes.isEmpty();
    }

    /** Why there is no forecast, or empty when there is one. */
    public Optional<Reason> reason() {
        if (hasForecast()) {
            return Optional.empty();
        }
        return Optional.of(remaining == 0 ? Reason.NOTHING_OPEN : Reason.TOO_LITTLE_HISTORY);
    }

    public Outcome outcome(int confidence) {
        return outcomes.stream().filter(outcome -> outcome.confidence() == confidence).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No outcome for " + confidence + "%"));
    }

    /** Cards completed in the history window. */
    public int completions() {
        return throughput.stream().mapToInt(Integer::intValue).sum();
    }

    /** Days in the history window on which at least one card was completed. */
    public long activeDays() {
        return throughput.stream().filter(count -> count > 0).count();
    }

    /** The pace the history implies, in cards per week. */
    public double perWeek() {
        return throughput.isEmpty() ? 0 : completions() * 7.0 / throughput.size();
    }

    /** The share of trials, 0 to 1, that had finished by the end of {@code date}. */
    public double chanceBy(LocalDate date) {
        if (!hasForecast()) {
            return 0;
        }
        long allowed = ChronoUnit.DAYS.between(today, date);
        long finished = distribution.stream().filter(bucket -> bucket.days() <= allowed).mapToLong(Bucket::trials).sum();
        return (double) finished / TRIALS;
    }

    /** The chance of clearing the board by its latest due date, when the open cards have one. */
    public Optional<Double> targetChance() {
        return hasForecast() && target != null ? Optional.of(chanceBy(target)) : Optional.empty();
    }

    /** The busiest finishing day's trial count, to scale a histogram. */
    public int peak() {
        return distribution.stream().mapToInt(Bucket::trials).max().orElse(0);
    }
}
