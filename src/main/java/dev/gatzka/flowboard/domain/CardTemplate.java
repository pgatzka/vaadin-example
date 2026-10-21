package dev.gatzka.flowboard.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A reusable card definition on a board; tags and checklist lines are stored joined. */
@Entity
@Table(name = "card_templates")
public class CardTemplate {

    private static final String TAG_SEPARATOR = ",";
    private static final String LINE_SEPARATOR = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 4000)
    private String description;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Column(length = 60)
    private String assignee;

    private Integer estimate;

    @Column(length = 500)
    private String tags;

    @Column(length = 2000)
    private String checklist;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CardTemplate() {
    }

    public CardTemplate(long boardId, CardTemplateDraft draft, Instant createdAt) {
        this.boardId = boardId;
        this.createdAt = createdAt;
        update(draft);
    }

    public void update(CardTemplateDraft draft) {
        this.name = draft.name();
        this.title = draft.toCardDraft().title();
        this.description = draft.description();
        this.priority = draft.priority();
        this.assignee = draft.assignee();
        this.estimate = draft.estimate();
        this.tags = draft.tags().isEmpty() ? null : String.join(TAG_SEPARATOR, draft.tags());
        this.checklist = draft.checklist().isEmpty() ? null : String.join(LINE_SEPARATOR, draft.checklist());
    }

    public CardTemplateDraft toDraft() {
        return new CardTemplateDraft(name, title, description, priority, assignee, getTags(), estimate, getChecklist());
    }

    public Long getId() {
        return id;
    }

    public Long getBoardId() {
        return boardId;
    }

    public String getName() {
        return name;
    }

    public Set<String> getTags() {
        return tags == null ? Set.of() : Arrays.stream(tags.split(TAG_SEPARATOR)).collect(Collectors.toUnmodifiableSet());
    }

    public List<String> getChecklist() {
        return checklist == null ? List.of() : List.of(checklist.split(LINE_SEPARATOR));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
