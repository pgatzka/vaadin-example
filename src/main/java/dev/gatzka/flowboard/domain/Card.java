package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Priority priority;

    private LocalDate dueDate;

    @Column(length = 60)
    private String assignee;

    /** Story points, or {@code null} when not estimated. */
    private Integer estimate;

    @ElementCollection
    @CollectionTable(name = "card_tags", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new HashSet<>();

    /*
     * Both child collections are sets, not lists: when several collections are fetch-joined in
     * one query, Hibernate de-duplicates sets but would hand back a list with repeated elements.
     * Ordering is applied in the accessors instead of relying on iteration order.
     */
    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ChecklistItem> checklist = new LinkedHashSet<>();

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Comment> comments = new LinkedHashSet<>();

    @Column(name = "sort_order", nullable = false)
    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** When the card last entered a done lane; {@code null} while it is open. */
    private Instant completedAt;

    /** When the card was archived; {@code null} while it is on the board. */
    private Instant archivedAt;

    /** When the card entered its current lane. */
    @Column(nullable = false)
    private Instant laneEnteredAt;

    protected Card() {
        // JPA
    }

    public Card(Lane lane, CardDraft draft, int position, Instant now) {
        this.lane = requireNonNull(lane);
        this.position = position;
        this.createdAt = requireNonNull(now);
        this.completedAt = lane.isDone() ? now : null;
        this.laneEnteredAt = now;
        update(draft, now);
    }

    public void update(CardDraft draft, Instant now) {
        this.title = draft.title();
        this.description = draft.description();
        this.priority = draft.priority();
        this.dueDate = draft.dueDate();
        this.assignee = draft.assignee();
        this.estimate = draft.estimate();
        this.tags.clear();
        this.tags.addAll(draft.tags());
        this.updatedAt = requireNonNull(now);
    }

    /** Moves the card to another lane; a move within the same lane keeps the lane entry time. */
    public void moveTo(Lane lane, Instant now) {
        requireNonNull(lane);
        if (!lane.getId().equals(this.lane.getId())) {
            this.laneEnteredAt = requireNonNull(now);
        }
        this.lane = lane;
        this.updatedAt = requireNonNull(now);
        markCompletion(lane.isDone(), now);
    }

    /** Keeps {@link #getCompletedAt()} in step with whether the card sits in a done lane. */
    public void markCompletion(boolean done, Instant now) {
        if (done && completedAt == null) {
            completedAt = requireNonNull(now);
        } else if (!done) {
            completedAt = null;
        }
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public ChecklistItem addChecklistItem(String text, Instant now) {
        ChecklistItem item = new ChecklistItem(this, text, checklist.size());
        checklist.add(item);
        updatedAt = requireNonNull(now);
        return item;
    }

    public void removeChecklistItem(ChecklistItem item, Instant now) {
        if (checklist.remove(item)) {
            List<ChecklistItem> remaining = getChecklist();
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).setPosition(i);
            }
            updatedAt = requireNonNull(now);
        }
    }

    public Comment addComment(String author, String text, Instant now) {
        Comment comment = new Comment(this, author, text, now);
        comments.add(comment);
        updatedAt = requireNonNull(now);
        return comment;
    }

    public void archive(Instant now) {
        archivedAt = requireNonNull(now);
        updatedAt = now;
    }

    public void restore(int position, Instant now) {
        archivedAt = null;
        this.position = position;
        this.laneEnteredAt = requireNonNull(now);
        updatedAt = now;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public void touch(Instant now) {
        updatedAt = requireNonNull(now);
    }

    public Long getId() {
        return id;
    }

    public Lane getLane() {
        return lane;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getAssignee() {
        return assignee;
    }

    public Integer getEstimate() {
        return estimate;
    }

    public Set<String> getTags() {
        return tags;
    }

    /** Comments oldest first; read-only, modify through the card. */
    public List<Comment> getComments() {
        return comments.stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt).thenComparing(Comment::getId))
                .toList();
    }

    /** The checklist in order; read-only, modify through the card. */
    public List<ChecklistItem> getChecklist() {
        return checklist.stream().sorted(Comparator.comparingInt(ChecklistItem::getPosition)).toList();
    }

    public int getPosition() {
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getLaneEnteredAt() {
        return laneEnteredAt;
    }
}
