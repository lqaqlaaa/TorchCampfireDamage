package com.torchcampfiredamage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> CHUNK_RADIUS;
    public static final ModConfigSpec.ConfigValue<Double> DAMAGE_AMOUNT;
    public static final ModConfigSpec.ConfigValue<Integer> DAMAGE_INTERVAL;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_CAMPFIRE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_TORCH;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_SOUL_CAMPFIRE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_SOUL_TORCH;
    public static final ModConfigSpec.ConfigValue<Boolean> TELEPORT_DROPS;
    public static final ModConfigSpec.ConfigValue<Boolean> TELEPORT_XP;
    public static final ModConfigSpec.ConfigValue<Boolean> COUNT_AS_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<Boolean> IGNORE_NAMED_MOBS;
    public static final ModConfigSpec.ConfigValue<Boolean> TREAT_PROVOKED_AS_HOSTILE;
    public static final ModConfigSpec.ConfigValue<Boolean> ANNOUNCE_KILLS;
    public static final ModConfigSpec.ConfigValue<Double> ANNOUNCE_RANGE;
    public static final ModConfigSpec.ConfigValue<Boolean> PLAY_SOUND;
    public static final ModConfigSpec.ConfigValue<Boolean> GLOW_HIT_TARGETS;
    public static final ModConfigSpec.ConfigValue<Integer> GLOW_DURATION;
    public static final ModConfigSpec.ConfigValue<Boolean> SLOW_HIT_TARGETS;
    public static final ModConfigSpec.ConfigValue<Integer> SLOW_AMPLIFIER;
    public static final ModConfigSpec.ConfigValue<Integer> SLOW_DURATION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Torch Campfire Damage Configuration").push("general");

        CHUNK_RADIUS = builder
                .comment("区块范围。以火源所在区块为中心，向四周扩展的区块圈数：",
                        "0 = 1x1 区块（只有火源所在那一个区块生效）",
                        "1 = 3x3 区块（中心 + 周围一圈）",
                        "2 = 5x5 区块",
                        "3 = 7x7 区块",
                        "4 = 9x9 区块")
                .defineInRange("chunkRadius", 1, 0, 4);

        DAMAGE_AMOUNT = builder
                .comment("每次伤害量（半颗心为单位，2.0 = 1颗心）。无上限限制。")
                .define("damageAmount", 2.0);

        DAMAGE_INTERVAL = builder
                .comment("伤害间隔（tick，20 tick = 1秒，1 = 每tick伤害）。最小 1，无上限。")
                .defineInRange("damageInterval", 20, 1, Integer.MAX_VALUE);

        ENABLE_CAMPFIRE = builder
                .comment("是否启用普通篝火").define("enableCampfire", true);
        ENABLE_TORCH = builder
                .comment("是否启用普通火把").define("enableTorch", true);
        ENABLE_SOUL_CAMPFIRE = builder
                .comment("是否启用灵魂篝火").define("enableSoulCampfire", true);
        ENABLE_SOUL_TORCH = builder
                .comment("是否启用灵魂火把").define("enableSoulTorch", true);

        TELEPORT_DROPS = builder
                .comment("是否将掉落物传送到篝火/火把位置").define("teleportDrops", true);

        TELEPORT_XP = builder
                .comment("是否将经验球传送到篝火/火把位置").define("teleportExperience", true);

        COUNT_AS_PLAYER_KILL = builder
                .comment("是否算作玩家击杀（触发玩家击杀的掉落物和经验）").define("countAsPlayerKill", true);

        IGNORE_NAMED_MOBS = builder
                .comment("是否忽略被命名的生物").define("ignoreNamedMobs", true);

        TREAT_PROVOKED_AS_HOSTILE = builder
                .comment("中立生物（僵尸猪灵、末影人、蜜蜂、狼、铁傀儡等）一旦攻击玩家",
                        "或有玩家方的攻击目标，也会被视为敌对并被伤害。")
                .define("treatProvokedAsHostile", true);

        ANNOUNCE_KILLS = builder
                .comment("击杀时在聊天栏播报：哪个位置的火源击杀了什么怪物")
                .define("announceKills", true);

        ANNOUNCE_RANGE = builder
                .comment("击杀播报的可见半径（方块）。范围内的玩家都会看到播报。")
                .defineInRange("announceRange", 64.0, 1.0, 10000.0);

        PLAY_SOUND = builder
                .comment("经验被吸收到火源位置时播放音效").define("playSound", true);

        // ---- 命中附加效果 ----
        GLOW_HIT_TARGETS = builder
                .comment("被火源击中的目标是否获得发光效果（便于在黑暗中定位）")
                .define("glowHitTargets", false);

        GLOW_DURATION = builder
                .comment("发光持续时间（tick，20 tick = 1秒）。每次命中都会刷新。")
                .defineInRange("glowDuration", 100, 1, 100000);

        SLOW_HIT_TARGETS = builder
                .comment("被火源击中的目标是否被赋予减速效果")
                .define("slowHitTargets", false);

        SLOW_AMPLIFIER = builder
                .comment("减速等级（0 = 缓慢I，1 = 缓慢II，…… 254 = 缓慢255）。填写等级而非实际数值。")
                .defineInRange("slowAmplifier", 254, 0, 254);

        SLOW_DURATION = builder
                .comment("减速持续时间（tick，20 tick = 1秒）。每次命中都会刷新。")
                .defineInRange("slowDuration", 100, 1, 100000);

        builder.pop();
        SPEC = builder.build();
    }
}