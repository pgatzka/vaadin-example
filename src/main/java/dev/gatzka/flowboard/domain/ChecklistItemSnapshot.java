package dev.gatzka.flowboard.domain;

public record ChecklistItemSnapshot(long id, String text, boolean done, int position) {

    public static ChecklistItemSnapshot of(ChecklistItem item) {
        return new ChecklistItemSnapshot(item.getId(), item.getText(), item.isDone(), item.getPosition());
    }
}
