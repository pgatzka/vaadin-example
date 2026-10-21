package dev.gatzka.flowboard.domain;

import java.util.Locale;

/** What a lane automation does. Most run when a card enters the lane; one runs nightly. */
public enum RuleAction {
    ASSIGN("Assign to", "a name", true),
    ADD_TAG("Add the tag", "a tag", true),
    SET_PRIORITY("Set priority to", "low, medium, high or urgent", true),
    DUE_IN_DAYS("Set the due date to today plus", "a number of days", true),
    CLEAR_DUE("Clear the due date", null, false),
    NOTIFY("Notify", "a name", true),
    ARCHIVE_AFTER_DAYS("Archive cards that have been here for more than", "a number of days (checked nightly)", true);

    private final String label;
    private final String argumentHint;
    private final boolean needsArgument;

    RuleAction(String label, String argumentHint, boolean needsArgument) {
        this.label = label;
        this.argumentHint = argumentHint;
        this.needsArgument = needsArgument;
    }

    public String label() {
        return label;
    }

    public String argumentHint() {
        return argumentHint;
    }

    public boolean needsArgument() {
        return needsArgument;
    }

    /** Whether the action fires when a card enters the lane (as opposed to nightly). */
    public boolean runsOnEnter() {
        return this != ARCHIVE_AFTER_DAYS;
    }

    /** Returns the normalised argument, or throws when it is not usable for this action. */
    public String normalise(String argument) {
        String cleaned = argument == null ? "" : argument.strip();
        if (!needsArgument) {
            return null;
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("This rule needs " + argumentHint);
        }
        return switch (this) {
            case SET_PRIORITY -> {
                try {
                    yield Priority.valueOf(cleaned.toUpperCase(Locale.ROOT)).name();
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Priority must be low, medium, high or urgent");
                }
            }
            case DUE_IN_DAYS, ARCHIVE_AFTER_DAYS -> {
                try {
                    int days = Integer.parseInt(cleaned);
                    if (days < 0 || days > 3650) {
                        throw new NumberFormatException();
                    }
                    yield Integer.toString(days);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Days must be a whole number between 0 and 3650");
                }
            }
            default -> {
                if (cleaned.length() > 120) {
                    throw new IllegalArgumentException("Keep it under 120 characters");
                }
                yield cleaned;
            }
        };
    }

    /** A sentence describing the rule, e.g. "Assign to Mara". */
    public String describe(String argument) {
        return switch (this) {
            case DUE_IN_DAYS -> "Due " + (argument.equals("0") ? "today" : "in " + argument + (argument.equals("1") ? " day" : " days"));
            case ARCHIVE_AFTER_DAYS -> "Archive after " + argument + (argument.equals("1") ? " day" : " days") + " here";
            case SET_PRIORITY -> "Priority " + Priority.valueOf(argument).label();
            case CLEAR_DUE -> label;
            default -> label + " " + argument;
        };
    }
}
