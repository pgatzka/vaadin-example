package dev.gatzka.flowboard.domain;

import java.util.List;

/**
 * The home page's numbers across every board.
 *
 * @param needsAttention overdue open cards, most overdue first
 * @param recentActivity latest entries across boards, newest first
 */
public record Overview(
        int boards,
        int openCards,
        int overdueCards,
        int dueThisWeek,
        int completedLast7Days,
        List<CardLocation> needsAttention,
        List<ActivityLocation> recentActivity) {

    /** An activity entry together with the board it happened on. */
    public record ActivityLocation(long boardId, String boardName, ActivityEntry entry) {
    }

    public boolean isEmpty() {
        return boards == 0;
    }
}
