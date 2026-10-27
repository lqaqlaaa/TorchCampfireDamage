package com.torchcampfiredamage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 全局配置 —— 每个守卫方块的详细参数在游戏内 GUI 里设置，
 * 这里只保留全局性的行为开关。
 */
public class ModConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<Boolean> TELEPORT_DROPS;
    public static final ModConfigSpec.ConfigValue<Boolean> TELEPORT_XP;
    public static final ModConfigSpec.ConfigValue<Boolean> COUNT_AS_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<Boolean> IGNORE_NAMED_MOBS;
    public static final ModConfigSpec.ConfigValue<Boolean> TREAT_PROVOKED_AS_HOSTILE;
    public static final ModConfigSpec.ConfigValue<Boolean> GLOBAL_ANNOUNCE_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> ANNOUNCE_RANGE;
    public static final ModConfigSpec.ConfigValue<Boolean> PLAY_SOUND;
    public static final ModConfigSpec.ConfigValue<Integer> GLOW_DURATION;
    public static final ModConfigSpec.ConfigValue<Integer> SLOW_DURATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Torch Campfire Damage - Global Settings",
                        "Per-block settings (radius/damage/interval/flags) are configured in-game",
                        "by sneak + right-clicking a Ward Torch or Ward Campfire.")
                .push("general");

        TELEPORT_DROPS = builder
                .comment("将掉落物传送到守卫方块位置")
                .define("teleportDrops", true);

        TELEPORT_XP = builder
                .comment("将经验球传送到守卫方块位置")
                .define("teleportExperience", true);

        COUNT_AS_PLAYER_KILL = builder
                .comment("伤害算作玩家击杀（触发玩家击杀掉落与经验）")
                .define("countAsPlayerKill", true);

        IGNORE_NAMED_MOBS = builder
                .comment("忽略被命名的生物")
                .define("ignoreNamedMobs", true);

        TREAT_PROVOKED_AS_HOSTILE = builder
                .comment("被激怒的中立生物（僵尸猪灵、末影人、蜜蜂、狼、铁傀儡等）也视为敌对")
                .define("treatProvokedAsHostile", true);

        GLOBAL_ANNOUNCE_ENABLED = builder
                .comment("击杀播报总开关（单个方块还可在 GUI 里单独关闭）")
                .define("globalAnnounceEnabled", true);

        ANNOUNCE_RANGE = builder
                .comment("击杀播报可见半径（方块）")
                .defineInRange("announceRange", 64.0, 1.0, 100000.0);

        PLAY_SOUND = builder
                .comment("经验被吸取时播放音效")
                .define("playSound", true);

        GLOW_DURATION = builder
                .comment("发光持续时长（tick，20 = 1秒）")
                .defineInRange("glowDuration", 100, 1, 100000);

        SLOW_DURATION = builder
                .comment("减速持续时长（tick，20 = 1秒）")
                .defineInRange("slowDuration", 100, 1, 100000);

        builder.pop();
        SPEC = builder.build();
    }
}