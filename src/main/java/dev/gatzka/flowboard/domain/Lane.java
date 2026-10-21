package dev.gatzka.flowboard.domain;

import static java.util.Objects.requireNonNull;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A column on a board. Cards reference their lane; the lane itself only knows its order. */
@Entity
@Table(name = "lanes")
public class Lane {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int position;

    private Integer wipLimit;

    @Column(nullable = false)
    private boolean done;

    protected Lane() {
        // JPA
    }

    public Lane(Board board, LaneDraft draft, int position) {
        this.board = requireNonNull(board);
        this.position = position;
        update(draft);
    }

    public void update(LaneDraft draft) {
        this.name = draft.name();
        this.wipLimit = draft.wipLimit();
        this.done = draft.done();
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Board getBoard() {
        return board;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public Integer getWipLimit() {
        return wipLimit;
    }

    public boolean isDone() {
        return done;
    }
}
