package dev.gatzka.flowboard.domain;

/** A saved view as the UI needs it. */
public record SavedViewSnapshot(long id, String name, CardFilter filter) {

    public static SavedViewSnapshot of(SavedView view) {
        return new SavedViewSnapshot(view.getId(), view.getName(), CardFilterCodec.decode(view.getFilter()));
    }
}
