package dev.gatzka.flowboard.domain;

/** A template as the UI shows it. */
public record CardTemplateSnapshot(long id, CardTemplateDraft draft) {

    public static CardTemplateSnapshot of(CardTemplate template) {
        return new CardTemplateSnapshot(template.getId(), template.toDraft());
    }

    public String name() {
        return draft.name();
    }
}
