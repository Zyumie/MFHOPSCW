package fr.zyumie.quest.model;

public class FactionQuestProgress {

    private final String questId;
    private final String factionName;
    private int progress;
    private boolean completed;

    public FactionQuestProgress(String questId, String factionName) {
        this.questId = questId;
        this.factionName = factionName;
        this.progress = 0;
        this.completed = false;
    }

    public FactionQuestProgress(String questId, String factionName, int progress, boolean completed) {
        this.questId = questId;
        this.factionName = factionName;
        this.progress = progress;
        this.completed = completed;
    }

    public String getQuestId()     { return questId; }
    public String getFactionName() { return factionName; }
    public int getProgress()       { return progress; }
    public boolean isCompleted()   { return completed; }

    public void addProgress(int amount) {
        if (!completed) this.progress += amount;
    }

    public void setCompleted() {
        this.completed = true;
    }
}
