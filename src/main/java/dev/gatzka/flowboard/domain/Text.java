package dev.gatzka.flowboard.domain;

/** Normalisation helpers shared by the domain value objects. */
final class Text {

    private Text() {
    }

    /** Trims the value and collapses blank input to {@code null}. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Like {@link #clean(String)} but rejects blank input. */
    static String require(String value, String field) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return cleaned;
    }
}
