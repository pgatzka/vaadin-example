package dev.gatzka.flowboard.ui.board;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuickAddComposerTest {

    @Test
    void pastedLinesLoseListMarkersAndBlankLines() {
        assertThat(QuickAddComposer.splitLines("- Buy milk\n* Call mum\n\n  3. Pay rent  \n• Sleep"))
                .containsExactly("Buy milk", "Call mum", "Pay rent", "Sleep");
        assertThat(QuickAddComposer.splitLines(null)).isEmpty();
        assertThat(QuickAddComposer.splitLines("   \n  ")).isEmpty();
    }

    @Test
    void pasteIsCapped() {
        String many = "line\n".repeat(QuickAddComposer.MAX_PASTED_LINES + 10);
        assertThat(QuickAddComposer.splitLines(many)).hasSize(QuickAddComposer.MAX_PASTED_LINES);
    }
}
