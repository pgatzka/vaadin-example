package dev.gatzka.flowboard.ui.board;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gatzka.flowboard.ui.FlowboardUiTest;import static org.awaitility.Awaitility.await;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.badge.Badge;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.contextmenu.MenuItem;
import dev.gatzka.flowboard.domain.Recurrence;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import dev.gatzka.flowboard.domain.BoardDraft;
import dev.gatzka.flowboard.domain.BoardSnapshot;
import dev.gatzka.flowboard.domain.CardDraft;
import dev.gatzka.flowboard.domain.CardSnapshot;
import dev.gatzka.flowboard.domain.Priority;
import dev.gatzka.flowboard.service.AttachmentService;
import dev.gatzka.flowboard.service.BoardService;
import dev.gatzka.flowboard.service.CommentService;
import dev.gatzka.flowboard.service.DependencyService;
import dev.gatzka.flowboard.ui.UserSession;
import dev.gatzka.flowboard.ui.boards.BoardsView;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "mara")
class BoardViewTest extends FlowboardUiTest {

    private static final String SEEDER = "Seeder";

    @Autowired
    private BoardService service;

    @Autowired
    private CommentService commentService;

    @Autowired
    private DependencyService dependencyService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private ApplicationContext context;

    private BoardSnapshot board;

    @BeforeEach
    void seedBoard() {
        service.listBoards().forEach(existing -> service.deleteBoard(existing.id(), SEEDER));
        board = service.createBoard(new BoardDraft("UI board", "For the view tests"), SEEDER);
    }

    @Test
    void rendersEveryLaneWithItsName() {
        openBoard();

        assertThat($(LaneComponent.class).all()).extracting(lane -> lane.lane().name())
                .containsExactly("Backlog", "In progress", "Review", "Done");
    }

    @Test
    void quickAddCreatesCardsWithoutLeavingTheLane() {
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();

        test($(Button.class, backlog).withText("Add card").single()).click();
        TextField title = $(TextField.class, backlog).withPlaceholder("Card title").single();
        test(title).setValue("First");
        test($(Button.class, backlog).withText("Add").single()).click();

        assertThat(visibleCardTitles()).containsExactly("First");
        QuickAddComposer composer = $(QuickAddComposer.class).first();
        assertThat(composer.isOpen()).as("composer stays open after a re-render for rapid entry").isTrue();

        TextField again = $(TextField.class, $(LaneComponent.class).first()).withPlaceholder("Card title").single();
        test(again).setValue("Second");
        test($(Button.class, $(LaneComponent.class).first()).withText("Add").single()).click();
        assertThat(visibleCardTitles()).containsExactly("First", "Second");
    }

    @Test
    void quickAddMoreOptionsOpensEditorWithTypedTitle() {
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();
        test($(Button.class, backlog).withText("Add card").single()).click();
        test($(TextField.class, backlog).withPlaceholder("Card title").single()).setValue("Needs details");

        test($(Button.class, backlog).withCondition(b -> "Open the full card editor".equals(b.getAriaLabel().orElse(null))).single()).click();

        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        assertThat($(TextField.class, dialog).withLabel("Title").single().getValue()).isEqualTo("Needs details");
    }

    @Test
    void addingCardThroughTheDialogShowsItInTheLane() {
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();

        test($(Button.class, backlog).withText("Add card").single()).click();
        test($(Button.class, backlog).withCondition(b -> "Open the full card editor".equals(b.getAriaLabel().orElse(null))).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TextField.class, dialog).withLabel("Title").single()).setValue("Write view tests");
        test($(Button.class, dialog).withText("Add card").single()).click();

        assertThat($(CardEditorDialog.class).all()).as("dialog closes after saving").isEmpty();
        assertThat(visibleCardTitles()).containsExactly("Write view tests");
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("Write view tests");
    }

    @Test
    void cardWithoutTitleIsRejected() {
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();
        test($(Button.class, backlog).withText("Add card").single()).click();
        test($(Button.class, backlog).withCondition(b -> "Open the full card editor".equals(b.getAriaLabel().orElse(null))).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();

        test($(Button.class, dialog).withText("Add card").single()).click();

        assertThat(dialog.isOpened()).isTrue();
        assertThat($(TextField.class, dialog).withLabel("Title").single().isInvalid()).isTrue();
        assertThat(visibleCardTitles()).isEmpty();
    }

    @Test
    void editingCardUpdatesIt() {
        CardSnapshot card = createCard("Draft title");
        openBoard();

        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TextField.class, dialog).withLabel("Title").single()).setValue("Final title");
        test($(Button.class, dialog).withText("Save").single()).click();

        assertThat(visibleCardTitles()).containsExactly("Final title");
        assertThat(service.findBoard(board.id()).orElseThrow().lane(card.laneId()).orElseThrow().cards())
                .extracting(CardSnapshot::title).containsExactly("Final title");
    }

    @Test
    void filterHidesCardsThatDoNotMatch() {
        createCard("Alpha release");
        createCard("Beta feedback");
        openBoard();

        test($(TextField.class).withPlaceholder("Filter cards").single()).setValue("beta");

        assertThat(visibleCardTitles()).containsExactly("Beta feedback");
    }

    @Test
    void structuredFiltersNarrowTheBoard() {
        long backlog = board.lanes().get(0).id();
        service.createCard(backlog, new CardDraft("Late and mine", null, Priority.HIGH, LocalDate.now().minusDays(2), null, Set.of(), null), SEEDER);
        service.createCard(backlog, new CardDraft("On time", null, Priority.LOW, LocalDate.now().plusDays(2), null, Set.of(), null), SEEDER);
        openBoard();

        test($(Checkbox.class).withLabel("Overdue only").single()).click();
        assertThat(visibleCardTitles()).containsExactly("Late and mine");
        assertThat($(FilterMenu.class).single().filter().toggleCount()).isEqualTo(1);

        test($(Button.class).withText("Clear filters").single()).click();
        assertThat(visibleCardTitles()).containsExactly("Late and mine", "On time");
    }

    @Test
    void deletingLaneOffersToMoveItsCards() {
        createCard("Keep me");
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();

        test($(MenuItem.class, backlog).withText("Delete lane").single()).click();
        DeleteLaneDialog dialog = $(DeleteLaneDialog.class).single();
        RadioButtonGroup<DeleteLaneDialog.Fate> fate = $(RadioButtonGroup.class, dialog).single();
        assertThat(fate.getValue()).isEqualTo(DeleteLaneDialog.Fate.MOVE);
        test($(Button.class, dialog).withText("Delete lane").single()).click();

        assertThat($(LaneComponent.class).all()).extracting(lane -> lane.lane().name())
                .containsExactly("In progress", "Review", "Done");
        assertThat($(LaneComponent.class).first().lane().cards()).extracting(CardSnapshot::title).containsExactly("Keep me");
    }

    @Test
    void checklistIsEditedInPlaceAndSummarisedOnTheCard() {
        createCard("With checklist");
        openBoard();
        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();

        test($(TextField.class, dialog).withPlaceholder("Add an item and press Enter").single()).setValue("Step one");
        test($(Button.class, dialog).withText("Add").single()).click();
        test($(Checkbox.class, dialog).withLabel("Step one").single()).click();

        CardComponent card = $(CardComponent.class).single();
        assertThat(card.card().checklist()).extracting(c -> c.done()).containsExactly(true);
        assertThat($(Badge.class, card).withClassName("task-card__checklist").single().getText()).isEqualTo("1/1");
    }

    @Test
    void commentsAreAddedFromTheEditorAndCountedOnTheCard() {
        CardSnapshot created = createCard("Talk about it");
        openBoard();
        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();

        test($(MessageInput.class, dialog).single()).send("Looks good to me");

        assertThat(test($(MessageList.class, $(CommentsSection.class).single()).single()).getMessages())
                .extracting(MessageListItem::getText).containsExactly("Looks good to me");
        assertThat($(Badge.class, $(CardComponent.class).single()).withClassName("task-card__comments").single().getText()).isEqualTo("1");
        assertThat(service.findBoard(board.id()).orElseThrow().lane(created.laneId()).orElseThrow().cards().getFirst().comments())
                .extracting(c -> c.author()).containsExactly(context.getBean(UserSession.class).getDisplayName());
    }

    @Test
    void historyTabListsWhatHappenedToTheCard() {
        CardSnapshot created = createCard("Tracked");
        service.updateCard(created.id(), new CardDraft("Tracked closely", null, Priority.HIGH, null, null, Set.of(), null), SEEDER);
        openBoard();
        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TabSheet.class, dialog).withClassName("card-tabs").single()).select("History");

        List<MessageListItem> history = test($(MessageList.class, $(HistorySection.class).single()).single()).getMessages();

        assertThat(history).extracting(MessageListItem::getText)
                .containsExactly("added \"Tracked\" to Backlog", "updated \"Tracked closely\"");
    }

    @Test
    void openEditorPicksUpCommentsMadeElsewhere() {
        CardSnapshot created = createCard("Shared");
        openBoard();
        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();

        service.updateCard(created.id(), created.toDraft(), SEEDER); // any change by somebody else
        comment(created.id(), "Priya", "Drive-by remark");

        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            pump();
            assertThat(test($(MessageList.class, $(CommentsSection.class, dialog).single()).single()).getMessages())
                    .extracting(MessageListItem::getText).containsExactly("Drive-by remark");
        });
    }

    @Test
    void insightsDialogShowsTheNumbers() {
        createCard("Counted");
        openBoard();

        test($(Button.class).withCondition(b -> "Show insights".equals(b.getAriaLabel().orElse(null))).single()).click();

        InsightsDialog dialog = $(InsightsDialog.class).single();
        assertThat(dialog.isOpened()).isTrue();
        assertThat($(com.vaadin.flow.component.html.Span.class, dialog).withClassName("insights__tile-value").all())
                .extracting(span -> span.getText()).startsWith("1", "0");
    }

    @Test
    void cardsCanBeArchivedFromTheEditorAndRestoredFromTheArchive() {
        createCard("Old news");
        openBoard();
        test($(CardComponent.class).single()).click();
        test($(Button.class, $(CardEditorDialog.class).single()).withText("Archive").single()).click();

        assertThat(visibleCardTitles()).isEmpty();

        test($(Button.class).withCondition(b -> "Show archive".equals(b.getAriaLabel().orElse(null))).single()).click();
        ArchiveDialog archive = $(ArchiveDialog.class).single();
        test($(Button.class, archive).withText("Restore").single()).click();

        assertThat(visibleCardTitles()).containsExactly("Old news");
        assertThat($(Button.class, archive).withText("Restore").all()).isEmpty();
    }

    @Test
    void lanesCanBeReorderedByDroppingOnAnotherLane() {
        BoardView view = navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));
        long backlog = board.lanes().get(0).id();
        int reviewPosition = board.lanes().get(2).position();

        view.moveLaneTo(backlog, reviewPosition);

        assertThat($(LaneComponent.class).all()).extracting(lane -> lane.lane().name())
                .containsExactly("In progress", "Review", "Backlog", "Done");
    }

    @Test
    void helpButtonListsShortcuts() {
        openBoard();
        test($(Button.class).withCondition(b -> "Keyboard shortcuts".equals(b.getAriaLabel().orElse(null))).single()).click();

        assertThat($(com.vaadin.flow.component.dialog.Dialog.class).withCondition(d -> "Keyboard shortcuts".equals(d.getHeaderTitle())).all()).hasSize(1);
    }

    @Test
    void estimateIsEditedInTheDialogAndShownOnCardAndLane() {
        createCard("Sized");
        openBoard();
        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();

        test($(IntegerField.class, dialog).withLabel("Estimate").single()).setValue(5);
        test($(Button.class, dialog).withText("Save").single()).click();

        assertThat($(Badge.class, $(CardComponent.class).single()).withClassName("task-card__estimate").single().getText()).isEqualTo("5 pt");
        assertThat($(Badge.class, $(LaneComponent.class).first()).withClassName("lane__points").single().getText()).isEqualTo("5 pt");
    }

    @Test
    void assistantIsHiddenWithoutAnApiKey() {
        createCard("Plain");
        openBoard();
        assertThat($(MenuItem.class).withText("Standup summary").all()).isEmpty();
        test($(CardComponent.class).single()).click();
        assertThat($(Button.class).withText("Suggest steps").all()).isEmpty();
    }

    @Test
    void votingFromTheCardTogglesTheVote() {
        createCard("Popular");
        openBoard();

        Button vote = $(Button.class, $(CardComponent.class).single()).withClassName("task-card__vote").single();
        test(vote).click();

        Button voted = $(Button.class, $(CardComponent.class).single()).withClassName("task-card__vote").single();
        assertThat(voted.getText()).isEqualTo("1");
        assertThat(voted.hasClassName("task-card__vote--mine")).isTrue();
        assertThat($(CardEditorDialog.class).all()).as("voting must not open the editor").isEmpty();

        test(voted).click();
        assertThat($(Button.class, $(CardComponent.class).single()).withClassName("task-card__vote").single().getText()).isEmpty();
    }

    @Test
    void pastingSeveralLinesCreatesSeveralCards() {
        openBoard();
        LaneComponent backlog = $(LaneComponent.class).first();
        test($(Button.class, backlog).withText("Add card").single()).click();

        $(QuickAddComposer.class, backlog).single().pasteLines("- First\n- Second\n\nThird");

        assertThat(visibleCardTitles()).containsExactly("First", "Second", "Third");
    }

    @Test
    void finishingACardIsCelebrated() {
        CardSnapshot card = createCard("Almost there");
        BoardView view = navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        view.moveCard(card.id(), board.lanes().get(3).id(), 0);

        Notification toast = $(Notification.class).withClassName("toast--undoable").single();
        assertThat($(Span.class, toast).first().getText()).contains("Done: Almost there");
        clickUndo();
        assertThat(cardOnBoard(card.id()).laneId()).isEqualTo(board.lanes().get(0).id());
    }

    @Test
    void keyboardMovesCardsAndOpensTheComposer() {
        CardSnapshot first = createCard("First");
        CardSnapshot second = createCard("Second");
        BoardView view = navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        view.moveCardByKeyboard(second, "ArrowUp");
        assertThat(titlesIn(0)).containsExactly("Second", "First");
        view.moveCardByKeyboard(cardOnBoard(second.id()), "ArrowUp");
        assertThat(titlesIn(0)).as("already at the top").containsExactly("Second", "First");
        view.moveCardByKeyboard(cardOnBoard(second.id()), "ArrowDown");
        assertThat(titlesIn(0)).containsExactly("First", "Second");
        view.moveCardByKeyboard(cardOnBoard(first.id()), "ArrowRight");
        assertThat(titlesIn(0)).containsExactly("Second");
        assertThat(titlesIn(1)).containsExactly("First");
        view.moveCardByKeyboard(cardOnBoard(first.id()), "ArrowLeft");
        assertThat(titlesIn(0)).containsExactly("First", "Second");
        view.moveCardByKeyboard(cardOnBoard(first.id()), "ArrowLeft");
        assertThat(titlesIn(0)).as("no lane to the left").containsExactly("First", "Second");

        view.addCardNear(cardOnBoard(first.id()));
        assertThat($(QuickAddComposer.class).all()).anySatisfy(composer -> assertThat(composer.isOpen()).isTrue());
    }

    private List<String> titlesIn(int lane) {
        return service.findBoard(board.id()).orElseThrow().lanes().get(lane).cards().stream().map(CardSnapshot::title).toList();
    }

    @Test
    void webhooksAreManagedFromTheBoard() {
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));
        test($(Button.class).withId("board-webhooks").single()).click();
        WebhooksDialog dialog = $(WebhooksDialog.class).single();

        $(TextField.class, dialog).withLabel("Endpoint URL").single().setValue("nope");
        test($(Button.class, dialog).withId("add-webhook").single()).click();
        assertThat($(TextField.class, dialog).withLabel("Endpoint URL").single().isInvalid()).isTrue();
        assertThat($(Div.class, dialog).withClassName("webhooks__row").all()).isEmpty();

        $(TextField.class, dialog).withLabel("Endpoint URL").single().setValue("https://example.com/hook");
        test($(Button.class, dialog).withId("add-webhook").single()).click();
        assertThat($(Div.class, dialog).withClassName("webhooks__row").all()).hasSize(1);
        assertThat($(Badge.class, dialog).withClassName("webhooks__status").single().getText()).isEqualTo("Not called yet");

        test($(Button.class, dialog).withClassName("remove-webhook").single()).click();
        assertThat($(Div.class, dialog).withClassName("webhooks__row").all()).isEmpty();
    }

    @Test
    void repeatsIsSetInTheEditorAndShownOnTheCard() {
        CardSnapshot card = createCard("Weekly review");
        BoardView view = navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));
        view.openCard(card);
        CardEditorDialog editor = $(CardEditorDialog.class).single();
        $(Select.class, editor).withClassName("card-recurrence").single().setValue(Recurrence.WEEKLY);
        test($(Button.class, editor).withText("Save").single()).click();

        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards().getFirst().recurrence()).isEqualTo(Recurrence.WEEKLY);
        assertThat($(Badge.class).withClassName("task-card__repeats").all()).hasSize(1);
    }

    @Test
    void movesAndLaneArchivingCanBeUndone() {
        CardSnapshot first = createCard("First");
        CardSnapshot second = createCard("Second");
        BoardView view = navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        view.moveCard(second.id(), board.lanes().get(1).id(), 0);
        assertThat(cardOnBoard(second.id()).laneId()).isEqualTo(board.lanes().get(1).id());
        clickUndo();
        CardSnapshot back = cardOnBoard(second.id());
        assertThat(back.laneId()).isEqualTo(board.lanes().get(0).id());
        assertThat(back.position()).isEqualTo(1);

        view.archiveLane(service.findBoard(board.id()).orElseThrow().lanes().get(0));
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards()).isEmpty();
        clickUndo();
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("First", "Second");
        assertThat($(CardComponent.class).all()).hasSize(2);
    }

    private CardSnapshot cardOnBoard(long cardId) {
        return service.findBoard(board.id()).orElseThrow().lanes().stream().flatMap(lane -> lane.cards().stream())
                .filter(card -> card.id() == cardId).findFirst().orElseThrow();
    }

    private void clickUndo() {
        Notification toast = $(Notification.class).withClassName("toast--undoable").last();
        test($(Button.class, toast).withText("Undo").single()).click();
        assertThat(toast.isOpened()).isFalse();
    }

    @Test
    void dependenciesAreEditedInTheDialogAndShownOnTheCard() {
        CardSnapshot design = createCard("Design");
        CardSnapshot build = createCard("Build");
        openBoard();
        test($(CardComponent.class).withCondition(c -> c.card().id() == build.id()).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TabSheet.class, dialog).withClassName("card-tabs").single()).select("Dependencies");

        test($(ComboBox.class, $(DependenciesSection.class).single()).single(), CardSnapshot.class).selectItem("Design");

        assertThat($(CardComponent.class).withCondition(c -> c.card().id() == build.id()).single().card().isBlocked()).isTrue();
        assertThat($(Badge.class, $(CardComponent.class).withCondition(c -> c.card().id() == build.id()).single())
                .withClassName("task-card__blocked").all()).hasSize(1);
        assertThat(design.id()).isNotEqualTo(build.id());
    }

    @Test
    void movingABlockedCardToDoneAsksFirst() {
        CardSnapshot design = createCard("Design");
        CardSnapshot build = createCard("Build");
        dependencyService.addBlocker(build.id(), design.id(), SEEDER);
        BoardView view = navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));

        view.moveCard(build.id(), board.lanes().get(3).id(), 0);

        ConfirmDialog confirm = $(ConfirmDialog.class).single();
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(3).cards()).as("nothing moves before confirming").isEmpty();
        test(confirm).confirm();
        assertThat(service.findBoard(board.id()).orElseThrow().lanes().get(3).cards()).extracting(CardSnapshot::title).containsExactly("Build");
    }

    @Test
    void attachmentsAreListedAndRemovableFromTheEditor() {
        CardSnapshot created = createCard("Has a file");
        attachmentService.attach(created.id(), "spec.pdf", "application/pdf", new byte[] {1, 2}, SEEDER);
        openBoard();
        assertThat($(Badge.class, $(CardComponent.class).single()).withClassName("task-card__attachments").single().getText()).isEqualTo("1");

        test($(CardComponent.class).single()).click();
        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        test($(TabSheet.class, dialog).withClassName("card-tabs").single()).select("Files (1)");
        assertThat($(Anchor.class, $(AttachmentsSection.class).single()).withText("spec.pdf").all()).hasSize(1);

        test($(Button.class, $(AttachmentsSection.class).single()).withCondition(b -> "Remove spec.pdf".equals(b.getAriaLabel().orElse(null))).single()).click();

        assertThat($(CardComponent.class).single().card().hasAttachments()).isFalse();
    }

    @Test
    void mentionsLightUpTheInboxBell() {
        UserSession me = context.getBean(UserSession.class);
        me.setDisplayName("Priya");
        CardSnapshot card = createCard("Ping me");
        openBoard();
        // The layout subscribed under the generated name; renaming announces itself like the profile dialog does.
        com.vaadin.flow.component.ComponentUtil.fireEvent(com.vaadin.flow.component.UI.getCurrent(),
                new dev.gatzka.flowboard.ui.ProfileChangedEvent(com.vaadin.flow.component.UI.getCurrent()));
        assertThat(unreadBadge().isVisible()).isFalse();

        comment(card.id(), "Jonas", "@Priya please check");

        await().pollInSameThread().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            pump();
            assertThat(unreadBadge().isVisible()).isTrue();
            assertThat(unreadBadge().getText()).isEqualTo("1");
        });

        test(inboxButton()).click();
        assertThat($(com.vaadin.flow.component.html.Div.class).withClassName("inbox__row--unread").all()).hasSize(1);
        test($(Button.class).withText("Mark all read").single()).click();
        assertThat(unreadBadge().isVisible()).isFalse();
    }

    private Button inboxButton() {
        return $(Button.class).withClassName("inbox-button").single();
    }

    private Badge unreadBadge() {
        return (Badge) inboxButton().getSuffixComponent();
    }

    @Test
    void changesMadeElsewhereArePushedIntoTheView() {
        openBoard();
        assertThat(visibleCardTitles()).isEmpty();

        createCard("Added from another session");

        // The push arrives on a background thread; roundTrip() must run on the thread owning the UI.
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            pump();
            assertThat(visibleCardTitles()).containsExactly("Added from another session");
        });
    }

    @Test
    void cardDeepLinkOpensTheEditor() {
        CardSnapshot created = createCard("Linked");

        navigate(BoardView.pathTo(board.id(), created.id()), BoardView.class);

        CardEditorDialog dialog = $(CardEditorDialog.class).single();
        assertThat(dialog.isOpened()).isTrue();
        assertThat(dialog.cardId()).contains(created.id());
        assertThat($(TextField.class, dialog).withLabel("Title").single().getValue()).isEqualTo("Linked");
    }

    @Test
    void deepLinkToMissingCardStillShowsTheBoard() {
        navigate(BoardView.pathTo(board.id(), 424242L), BoardView.class);

        assertThat($(CardEditorDialog.class).all()).isEmpty();
        assertThat($(LaneComponent.class).all()).hasSize(4);
        assertThat($(Notification.class).all()).isNotEmpty();
    }

    @Test
    void unknownBoardFallsBackToTheBoardList() {
        navigate("board/987654321", BoardsView.class);

        assertThat(getCurrentView()).isInstanceOf(BoardsView.class);
    }

    private void comment(long cardId, String author, String text) {
        commentService.add(cardId, author, text);
    }

    private void openBoard() {
        navigate(BoardView.class, Map.of(BoardView.BOARD_ID, Long.toString(board.id())));
    }

    private CardSnapshot createCard(String title) {
        long backlog = board.lanes().get(0).id();
        return service.createCard(backlog, new CardDraft(title, null, Priority.MEDIUM, null, null, Set.of(), null), SEEDER);
    }

    private List<String> visibleCardTitles() {
        return $(CardComponent.class).all().stream()
                .filter(Component::isVisible)
                .map(component -> component.card().title())
                .toList();
    }
}
