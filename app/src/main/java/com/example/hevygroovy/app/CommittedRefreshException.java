package com.example.hevygroovy.app;

/** The write committed. Recover by loading fresh state; never repeat the mutation. */
public final class CommittedRefreshException extends RuntimeException {
    private final Long selectedTemplateId;

    public CommittedRefreshException(Exception cause, Long selectedTemplateId) {
        super("Saved, but could not reload the screen", cause);
        this.selectedTemplateId = selectedTemplateId;
    }

    public Long getSelectedTemplateId() { return selectedTemplateId; }
}
