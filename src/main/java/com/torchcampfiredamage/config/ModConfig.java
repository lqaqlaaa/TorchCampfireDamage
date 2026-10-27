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
    public static final ModConfigSpec.ConfigValue<Boolean> COUNT_AS_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<Boolean> IGNORE_NAMED_MOBS;
    public static final ModConfigSpec.ConfigValue<Boolean> TREAT_PROVOKED_AS_HOSTILE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Torch Campfire Damage Configuration").push("general");

        CHUNK_RADIUS = builder
                .comment("区块范围半径。0 = 只有放置所在的那个区块生效（1x1 区块），",
                        "1 = 向外扩散 1 个区块（3x3），2 = 5x5，3 = 7x7，4 = 9x9。",
                        "以方块所在的区块为中心，整个受影响范围内的敌对生物都会被伤害。",
                        "范围过大可能影响性能，请谨慎设置。")
                .defineInRange("chunkRadius", 0, 0, 4);

        DAMAGE_AMOUNT = builder
                .comment("每次伤害量（半颗心为单位，2.0 = 1颗心）")
                .define("damageAmount", 2.0);

        DAMAGE_INTERVAL = builder
                .comment("伤害间隔（tick，20 tick = 1秒，1 = 每tick伤害），最小值 1")
                .defineInRange("damageInterval", 20, 1, Integer.MAX_VALUE);

        ENABLE_CAMPFIRE = builder
                .comment("是否启用普通篝火")
                .define("enableCampfire", true);

        ENABLE_TORCH = builder
                .comment("是否启用普通火把")
                .define("enableTorch", true);

        ENABLE_SOUL_CAMPFIRE = builder
                .comment("是否启用灵魂篝火")
                .define("enableSoulCampfire", true);

        ENABLE_SOUL_TORCH = builder
                .comment("是否启用灵魂火把")
                .define("enableSoulTorch", true);

        TELEPORT_DROPS = builder
                .comment("是否将掉落物传送到篝火/火把位置")
                .define("teleportDrops", true);

        COUNT_AS_PLAYER_KILL = builder
                .comment("是否算作玩家击杀（触发玩家击杀的掉落物和经验）")
                .define("countAsPlayerKill", true);

        IGNORE_NAMED_MOBS = builder
                .comment("是否忽略被命名的生物")
                .define("ignoreNamedMobs", true);

        TREAT_PROVOKED_AS_HOSTILE = builder
                .comment("当开启时，原本中立的生物（如僵尸猪灵、末影人、蜜蜂、狼、铁傀儡等）",
                        "一旦主动攻击玩家或有当前攻击目标，也会被视为敌对并被伤害。")
                .define("treatProvokedAsHostile", true);

        builder.pop();
        SPEC = builder.build();
    }
}