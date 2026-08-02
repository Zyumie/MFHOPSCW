package fr.zyumie.quest.model;

public class QuestDefinition {

    public enum QuestType { KILL_MOB, MINE_BLOCK, CRAFT_ITEM, TRAVEL_DISTANCE }

    private final String id;
    private final int week;
    private final String title;
    private final String description;
    private final QuestType type;
    private final String target;   // nom du mob/matériau (null pour TRAVEL)
    private final int amount;
    private final double rewardMoney;
    private final String rewardMessage;

    public QuestDefinition(String id, int week, String title, String description,
                           QuestType type, String target, int amount,
                           double rewardMoney, String rewardMessage) {
        this.id = id;
        this.week = week;
        this.title = title;
        this.description = description;
        this.type = type;
        this.target = target;
        this.amount = amount;
        this.rewardMoney = rewardMoney;
        this.rewardMessage = rewardMessage;
    }

    public String getId()            { return id; }
    public int getWeek()             { return week; }
    public String getTitle()         { return title; }
    public String getDescription()   { return description; }
    public QuestType getType()       { return type; }
    public String getTarget()        { return target; }
    public int getAmount()           { return amount; }
    public double getRewardMoney()   { return rewardMoney; }
    public String getRewardMessage() { return rewardMessage; }
}
