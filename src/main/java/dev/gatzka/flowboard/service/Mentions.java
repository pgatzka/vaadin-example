package dev.gatzka.flowboard.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds {@code @name} mentions in text. Names may contain letters, digits, dots, dashes and underscores. */
public final class Mentions {

    private static final Pattern MENTION = Pattern.compile("(?<![\\p{L}\\p{N}])@([\\p{L}\\p{N}][\\p{L}\\p{N}._-]{0,58})");

    private Mentions() {
    }

    /** Distinct mentioned names in order of appearance, trailing punctuation stripped. */
    public static Set<String> in(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null) {
            return names;
        }
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find()) {
            String name = matcher.group(1).replaceAll("[._-]+$", "");
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }
}
