package com.torchcampfiredamage.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<Integer> DAMAGE_RADIUS;
    public static final ModConfigSpec.ConfigValue<Double> DAMAGE_AMOUNT;
    public static final ModConfigSpec.ConfigValue<Integer> DAMAGE_INTERVAL;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_CAMPFIRE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_TORCH;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_SOUL_CAMPFIRE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_SOUL_TORCH;
    public static final ModConfigSpec.ConfigValue<Boolean> TELEPORT_DROPS;
    public static final ModConfigSpec.ConfigValue<Boolean> COUNT_AS_PLAYER_KILL;
    public static final ModConfigSpec.ConfigValue<Boolean> IGNORE_NAMED_MOBS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        
        builder.comment("Torch Campfire Damage Configuration")
               .push("general");

        DAMAGE_RADIUS = builder
                .comment("检测范围（方块）- 篝火和火把周围多少格内的敌对生物会受到伤害")
                .defineInRange("damageRadius", 8, 1, 64);

        DAMAGE_AMOUNT = builder
                .comment("每次伤害量（半颗心为单位，2.0 = 1颗心）")
                .defineInRange("damageAmount", 2.0, 0.5, 100.0);

        DAMAGE_INTERVAL = builder
                .comment("伤害间隔（tick，20 tick = 1秒）")
                .defineInRange("damageInterval", 20, 5, 200);

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

        builder.pop();
        SPEC = builder.build();
    }
}
