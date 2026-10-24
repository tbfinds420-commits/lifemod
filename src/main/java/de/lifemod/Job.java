package de.lifemod;

public enum Job {
    //          id            Name           Alter Bildung Gehalt Cooldown(Ticks) Ruhm-Job
    VERKAEUFER ("verkaeufer", "Verkäufer",   16,   0,   40,  1200, false),
    HANDWERKER ("handwerker", "Handwerker",  18,   8,   90,  1200, false),
    LEHRER     ("lehrer",     "Lehrer",      22,  20,  160,  1200, false),
    ARZT       ("arzt",       "Arzt",        25,  40,  400,  1200, false),
    RAPPER     ("rapper",     "Rapper",      16,   0,   20,  1200, true),
    INFLUENCER ("influencer", "Influencer",  14,   0,   15,  1200, true),
    MODEL      ("model",      "Model",       16,   0,   25,  1200, true);

    public final String id;
    public final String displayName;
    public final int minAge;
    public final int minEducation;
    public final int basePay;
    public final int cooldownTicks;
    public final boolean fameJob;

    Job(String id, String displayName, int minAge, int minEducation, int basePay, int cooldownTicks, boolean fameJob) {
        this.id = id;
        this.displayName = displayName;
        this.minAge = minAge;
        this.minEducation = minEducation;
        this.basePay = basePay;
        this.cooldownTicks = cooldownTicks;
        this.fameJob = fameJob;
    }

    public static Job byId(String id) {
        for (Job j : values()) {
            if (j.id.equalsIgnoreCase(id)) return j;
        }
        return null;
    }
}
