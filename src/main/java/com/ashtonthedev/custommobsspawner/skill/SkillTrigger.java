package com.ashtonthedev.custommobsspawner.skill;

public enum SkillTrigger {
    WHEN_HURT,
    WHEN_ATTACKS,
    WHEN_SPELL_CAST,
    ON_RANGED_HIT,
    FAILED_ATTACK,
    WHEN_KILLED,
    TIMED;

    public static SkillTrigger parse(String value) {
        return switch (value.toLowerCase()) {
            case "when_hurt" -> WHEN_HURT;
            case "when_attacks" -> WHEN_ATTACKS;
            case "when_spell_cast", "spell_cast" -> WHEN_SPELL_CAST;
            case "on_ranged_hit", "ranged_hit", "when_ranged_hit" -> ON_RANGED_HIT;
            case "failed_attack" -> FAILED_ATTACK;
            case "when_killed" -> WHEN_KILLED;
            case "timed" -> TIMED;
            default -> throw new IllegalArgumentException("Unknown skill trigger: " + value);
        };
    }
}
