package com.torchcampfiredamage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 全局配置。
 * <p>
 * 两个用途：
 * 1. 全局行为开关（掉落/经验传送、播报等）
 * 2. 新放置守卫火把时的「默认参数」—— 放下后可用 GUI 单独修改
 */
public class ModConfig {
    public static final ModConfigSpec SPEC;

    /* ===== 全局开关 ===== */
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

    /* ===== 放置时的默认参数 ===== */
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_ENABLED;
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_CHUNK_MODE;
    public static final ModConfigSpec.ConfigValue<Integer> DEFAULT_CHUNK_RADIUS;
    public static final ModConfigSpec.ConfigValue<Integer> DEFAULT_BLOCK_RADIUS;
    public static final ModConfigSpec.ConfigValue<Double> DEFAULT_DAMAGE;
    public static final ModConfigSpec.ConfigValue<Integer> DEFAULT_INTERVAL;
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_GLOWING;
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_SLOW;
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_ANNOUNCE;
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_TELEPORT_DROPS;
    public static final ModConfigSpec.ConfigValue<Boolean> DEFAULT_TELEPORT_XP;

    /* ===== 副手火把 ===== */
    public static final ModConfigSpec.ConfigValue<Boolean> OFFHAND_ENABLED;
    public static final ModConfigSpec.ConfigValue<Integer> OFFHAND_CHUNK_RADIUS;
    public static final ModConfigSpec.ConfigValue<Integer> OFFHAND_INTERVAL;
    public static final ModConfigSpec.ConfigValue<Boolean> OFFHAND_ANNOUNCE;
    public static final ModConfigSpec.ConfigValue<Boolean> OFFHAND_GLOWING;
    public static final ModConfigSpec.ConfigValue<Boolean> OFFHAND_SLOW;
    public static final ModConfigSpec.ConfigValue<Boolean> OFFHAND_TELEPORT_DROPS;
    public static final ModConfigSpec.ConfigValue<Boolean> OFFHAND_TELEPORT_XP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        /* ================= 全局 ================= */
        builder.comment("守卫火把 - 全局设置").push("general");

        TELEPORT_DROPS = builder
                .comment("掉落物传送到火把位置")
                .define("teleportDrops", true);

        TELEPORT_XP = builder
                .comment("经验传送到火把位置")
                .define("teleportExperience", true);

        COUNT_AS_PLAYER_KILL = builder
                .comment("伤害算作玩家击杀")
                .define("countAsPlayerKill", true);

        IGNORE_NAMED_MOBS = builder
                .comment("忽略被命名的生物")
                .define("ignoreNamedMobs", true);

        TREAT_PROVOKED_AS_HOSTILE = builder
                .comment("被激怒的中立生物也视为敌对")
                .define("treatProvokedAsHostile", true);

        GLOBAL_ANNOUNCE_ENABLED = builder
                .comment("击杀播报总开关")
                .define("globalAnnounceEnabled", true);

        ANNOUNCE_RANGE = builder
                .comment("播报可见半径（方块）")
                .defineInRange("announceRange", 64.0, 1.0, 100000.0);

        PLAY_SOUND = builder
                .comment("吸取经验时播放音效")
                .define("playSound", true);

        GLOW_DURATION = builder
                .comment("发光持续（tick）")
                .defineInRange("glowDuration", 100, 1, 100000);

        SLOW_DURATION = builder
                .comment("减速持续（tick）")
                .defineInRange("slowDuration", 100, 1, 100000);

        builder.pop();

        /* ================= 默认参数 ================= */
        builder.comment("新放置的守卫火把的默认参数（放下后可在游戏内 GUI 单独修改）")
                .push("defaults");

        DEFAULT_ENABLED = builder
                .comment("默认启用")
                .define("enabled", true);

        DEFAULT_CHUNK_MODE = builder
                .comment("默认使用区块模式（false = 方块模式）")
                .define("chunkMode", true);

        DEFAULT_CHUNK_RADIUS = builder
                .comment("默认区块半径（0 = 1x1, 1 = 3x3, 2 = 5x5 ...）")
                .defineInRange("chunkRadius", 1, 0, 32);

        DEFAULT_BLOCK_RADIUS = builder
                .comment("默认方块半径（格）")
                .defineInRange("blockRadius", 16, 0, 512);

        DEFAULT_DAMAGE = builder
                .comment("默认伤害（2.0 = 1颗心，0 = 只标记不伤害）")
                .define("damage", 2.0);

        DEFAULT_INTERVAL = builder
                .comment("默认伤害间隔（tick，20 = 1秒）")
                .defineInRange("interval", 20, 1, Integer.MAX_VALUE);

        DEFAULT_GLOWING = builder.comment("默认发光").define("glowing", false);
        DEFAULT_SLOW = builder.comment("默认减速").define("slow", false);
        DEFAULT_ANNOUNCE = builder.comment("默认击杀播报").define("announce", true);
        DEFAULT_TELEPORT_DROPS = builder.comment("默认传送掉落物").define("teleportDrops", true);
        DEFAULT_TELEPORT_XP = builder.comment("默认传送经验").define("teleportExperience", true);

        builder.pop();

        /* ================= 副手火把 ================= */
        builder.comment("副手持守卫火把时的效果")
                .push("offhand");

        OFFHAND_ENABLED = builder
                .comment("启用副手火把效果")
                .define("enabled", true);

        OFFHAND_CHUNK_RADIUS = builder
                .comment("副手火把的区块半径（1 = 3x3 区块）")
                .defineInRange("chunkRadius", 1, 0, 32);

        OFFHAND_INTERVAL = builder
                .comment("副手火把的伤害间隔（tick，1 = 每 tick）")
                .defineInRange("interval", 1, 1, Integer.MAX_VALUE);

        OFFHAND_ANNOUNCE = builder.comment("副手火把是否播报").define("announce", false);
        OFFHAND_GLOWING = builder.comment("副手火把是否发光").define("glowing", false);
        OFFHAND_SLOW = builder.comment("副手火把是否减速").define("slow", false);
        OFFHAND_TELEPORT_DROPS = builder.comment("副手火把是否传送掉落物").define("teleportDrops", false);
        OFFHAND_TELEPORT_XP = builder.comment("副手火把是否传送经验").define("teleportExperience", false);

        builder.pop();

        SPEC = builder.build();
    }
}