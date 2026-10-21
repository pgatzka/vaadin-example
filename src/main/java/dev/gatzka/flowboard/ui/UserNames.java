package dev.gatzka.flowboard.ui;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Generates friendly placeholder names for visitors who have not introduced themselves yet. */
final class UserNames {

    private static final List<String> ADJECTIVES = List.of(
            "Brisk", "Calm", "Clever", "Curious", "Gentle", "Keen", "Lively", "Nimble", "Quiet", "Swift");
    private static final List<String> ANIMALS = List.of(
            "Otter", "Falcon", "Lynx", "Heron", "Badger", "Dolphin", "Fox", "Ibis", "Marten", "Puffin");

    private UserNames() {
    }

    static String random() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return ADJECTIVES.get(random.nextInt(ADJECTIVES.size())) + " " + ANIMALS.get(random.nextInt(ANIMALS.size()));
    }
}
