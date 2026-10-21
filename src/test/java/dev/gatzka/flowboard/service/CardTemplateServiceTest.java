package dev.gatzka.flowboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.CardTemplateDraft;
import dev.gatzka.flowboard.domain.CardTemplateSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CardTemplateServiceTest {

    @Autowired
    private BoardService boardService;

    @Autowired
    private ChecklistService checklistService;

    @Autowired
    private CardTemplateService templates;

    @Autowired
    private ActivityLog activityLog;

    @Test
    void templatesProduceCardsWithChecklistsAndReplaceByName() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Templated", null), "Mara");
        CardTemplateDraft draft = new CardTemplateDraft(" Release ", null, "Cut a release", Priority.HIGH, "Mara",
                Set.of("ops", "Release"), 3, List.of("Tag", " ", "Build", "Announce"));

        CardTemplateSnapshot saved = templates.save(board.id(), draft, "Mara");
        assertThat(saved.name()).isEqualTo("Release");
        assertThat(saved.draft().title()).as("title defaults to the name").isEqualTo("Release");
        assertThat(saved.draft().checklist()).containsExactly("Tag", "Build", "Announce");
        assertThat(saved.draft().tags()).containsExactly("ops", "Release");

        CardSnapshot card = templates.instantiate(saved.id(), board.lanes().get(0).id(), "Jonas");
        assertThat(card.title()).isEqualTo("Release");
        assertThat(card.description()).isEqualTo("Cut a release");
        assertThat(card.priority()).isEqualTo(Priority.HIGH);
        assertThat(card.assignee()).isEqualTo("Mara");
        assertThat(card.estimate()).isEqualTo(3);
        assertThat(card.checklist()).extracting(item -> item.text()).containsExactly("Tag", "Build", "Announce");
        assertThat(activityLog.recent(board.id()).getFirst().message()).startsWith("added 3 checklist items to \"Release\" from the template \"Release\"");

        CardTemplateSnapshot replaced = templates.save(board.id(),
                new CardTemplateDraft("release", "Ship it", null, null, null, null, null, null), "Mara");
        assertThat(replaced.id()).isEqualTo(saved.id());
        assertThat(templates.forBoard(board.id())).hasSize(1);
        assertThat(templates.forBoard(board.id()).getFirst().draft().title()).isEqualTo("Ship it");
        assertThat(templates.forBoard(board.id()).getFirst().draft().priority()).isEqualTo(Priority.MEDIUM);
        CardSnapshot plain = templates.instantiate(saved.id(), board.lanes().get(1).id(), "Jonas");
        assertThat(plain.checklist()).isEmpty();
    }

    @Test
    void cardsBecomeTemplatesAndTemplatesCanBeDeleted() {
        BoardSnapshot board = boardService.createBoard(new BoardDraft("Templated", null), "Mara");
        CardSnapshot card = boardService.createCard(board.lanes().get(0).id(),
                new CardDraft("Onboard a teammate", "Laptop, accounts, buddy", Priority.MEDIUM, null, null, Set.of("people"), 5), "Mara");
        checklistService.addItem(card.id(), "Order laptop", "Mara");
        checklistService.addItem(card.id(), "Create accounts", "Mara");

        CardTemplateSnapshot template = templates.saveFromCard(card.id(), "Onboarding", "Mara");
        assertThat(template.draft().title()).isEqualTo("Onboard a teammate");
        assertThat(template.draft().estimate()).isEqualTo(5);
        assertThat(template.draft().tags()).containsExactly("people");
        assertThat(template.draft().checklist()).containsExactly("Order laptop", "Create accounts");

        templates.delete(template.id(), "Mara");
        templates.delete(template.id(), "Mara");
        assertThat(templates.forBoard(board.id())).isEmpty();
        assertThatThrownBy(() -> templates.instantiate(template.id(), board.lanes().get(0).id(), "Mara")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> new CardTemplateDraft(" ", null, null, null, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);

        templates.save(board.id(), new CardTemplateDraft("Left over", null, null, null, null, null, null, null), "Mara");
        boardService.deleteBoard(board.id(), "Mara");
        assertThat(templates.forBoard(board.id())).isEmpty();
    }
}
