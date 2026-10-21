package dev.gatzka.flowboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QuickAddTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 21);

    @Test
    void pullsEveryTokenOutOfTheTitle() {
        QuickAdd parsed = QuickAdd.parse("Fix login !high @mara #backend #Auth ~3 due:fri every:week", WEDNESDAY);
        CardDraft draft = parsed.draft();
        assertThat(draft.title()).isEqualTo("Fix login");
        assertThat(draft.priority()).isEqualTo(Priority.HIGH);
        assertThat(draft.assignee()).isEqualTo("mara");
        assertThat(draft.tags()).containsExactly("Auth", "backend");
        assertThat(draft.estimate()).isEqualTo(3);
        assertThat(draft.dueDate()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(parsed.recurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat(QuickAdd.usesShorthand("Fix login !high")).isTrue();
    }

    @Test
    void plainTitlesPassThroughUntouched() {
        QuickAdd parsed = QuickAdd.parse("  Call the plumber about the #1 issue  ", WEDNESDAY);
        assertThat(parsed.draft().title()).isEqualTo("Call the plumber about the issue");
        assertThat(parsed.draft().tags()).containsExactly("1");
        assertThat(QuickAdd.parse("Just a title", WEDNESDAY).draft()).isEqualTo(CardDraft.titled("Just a title"));
        assertThat(QuickAdd.usesShorthand("Just a title")).isFalse();
        assertThat(QuickAdd.parse("email me@example.com", WEDNESDAY).draft().title()).as("an @ inside a word is not an assignee")
                .isEqualTo("email me@example.com");
    }

    @Test
    void unknownValuesStayInTheTitle() {
        QuickAdd parsed = QuickAdd.parse("Odd !sometime ~lots due:never every:blue", WEDNESDAY);
        assertThat(parsed.draft().title()).isEqualTo("Odd !sometime ~lots due:never every:blue");
        assertThat(parsed.draft().priority()).isEqualTo(Priority.MEDIUM);
        assertThat(parsed.draft().estimate()).isNull();
        assertThat(parsed.draft().dueDate()).isNull();
        assertThat(parsed.recurrence()).isEqualTo(Recurrence.NONE);
        assertThat(QuickAdd.parse("!urgent", WEDNESDAY).draft().title()).as("tokens only: keep the text as title").isEqualTo("!urgent");
    }

    @Test
    void understandsRelativeDates() {
        assertThat(QuickAdd.date("today", WEDNESDAY)).isEqualTo(WEDNESDAY);
        assertThat(QuickAdd.date("tomorrow", WEDNESDAY)).isEqualTo(WEDNESDAY.plusDays(1));
        assertThat(QuickAdd.date("+10", WEDNESDAY)).isEqualTo(WEDNESDAY.plusDays(10));
        assertThat(QuickAdd.date("wed", WEDNESDAY)).as("same weekday means next week").isEqualTo(WEDNESDAY.plusDays(7));
        assertThat(QuickAdd.date("monday", WEDNESDAY)).isEqualTo(WEDNESDAY.plusDays(5));
        assertThat(QuickAdd.date("2026-12-24", WEDNESDAY)).isEqualTo(LocalDate.of(2026, 12, 24));
        assertThat(QuickAdd.date("someday", WEDNESDAY)).isNull();
        assertThat(QuickAdd.date("mo", WEDNESDAY)).as("too short to be a weekday").isNull();
    }
}
