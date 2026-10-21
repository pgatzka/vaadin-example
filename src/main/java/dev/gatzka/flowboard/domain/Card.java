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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
    @Column(nullable = false, length = 16)
    private Priority priority;

    private LocalDate dueDate;

    @Column(length = 60)
    private String assignee;

    @ElementCollection
    @CollectionTable(name = "card_tags", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new HashSet<>();

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ChecklistItem> checklist = new ArrayList<>();

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC, id ASC")
    private Set<Comment> comments = new LinkedHashSet<>();

    @Column(name = "sort_order", nullable = false)
    private int position;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Card() {
        // JPA
    }

    public Card(Lane lane, CardDraft draft, int position, Instant now) {
        this.lane = requireNonNull(lane);
        this.position = position;
        this.createdAt = requireNonNull(now);
        update(draft, now);
    }

    public void update(CardDraft draft, Instant now) {
        this.title = draft.title();
        this.description = draft.description();
        this.priority = draft.priority();
        this.dueDate = draft.dueDate();
        this.assignee = draft.assignee();
        this.tags.clear();
        this.tags.addAll(draft.tags());
        this.updatedAt = requireNonNull(now);
    }

    public void moveTo(Lane lane, Instant now) {
        this.lane = requireNonNull(lane);
        this.updatedAt = requireNonNull(now);
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
            for (int i = 0; i < checklist.size(); i++) {
                checklist.get(i).setPosition(i);
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

    public Set<String> getTags() {
        return tags;
    }

    /** Comments oldest first; read-only, modify through the card. */
    public List<Comment> getComments() {
        return List.copyOf(comments);
    }

    /** The checklist in order; read-only, modify through the card. */
    public List<ChecklistItem> getChecklist() {
        return Collections.unmodifiableList(checklist);
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
}
