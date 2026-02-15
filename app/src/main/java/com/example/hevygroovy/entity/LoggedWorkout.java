package com.example.hevygroovy.entity;

public class LoggedWorkout {

    private long id;
    private String nameSnapshot;
    private String notes;
    private long startedAtEpochMillis;
    private Long endedAtEpochMillis;
    private Long templateId;
    private long userId;

    public String getNotes() {
        return notes;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }



    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public void setNameSnapshot(String nameSnapshot) {
        this.nameSnapshot = nameSnapshot;
    }

    public long getStartedAtEpochMillis() {
        return startedAtEpochMillis;
    }

    public void setStartedAtEpochMillis(long startedAtEpochMillis) {
        this.startedAtEpochMillis = startedAtEpochMillis;
    }

    public Long getEndedAtEpochMillis() {
        return endedAtEpochMillis;
    }

    public void setEndedAtEpochMillis(Long endedAtEpochMillis) {
        this.endedAtEpochMillis = endedAtEpochMillis;
    }



}
