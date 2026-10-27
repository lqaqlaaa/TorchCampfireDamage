package com.torchcampfiredamage.events;

import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

public class DamageEventHandler {
    private static final int SCAN_STEP = 5;
    private static final int CHUNK_SIZE = SectionPos.SECTION_SIZE;

    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    private static final Map<UUID, BlockPos> entityDropTargets = new HashMap<>();
    private static final Map<Mob, Long> GLOW_EXPIRY = new WeakHashMap<>();

    /** 配置是否可用（避免未加载时异常导致整个 handler 失效） */
    private static volatile boolean configReady = false;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 服务端启动完成时配置一定已加载
        configReady = true;
        System.out.println("[TorchCampfireDamage] Mod loaded, config ready.");
    }

    /* ================= 配置安全读取 ================= */

    private static <T> T safeGet(java.util.function.Supplier<T> supplier, T def) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return def;
        }
    }

    /* ================= 火源判定 ================= */

    private static boolean isSourceBlock(BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() == Blocks.CAMPFIRE && safeGet(ModConfig.ENABLE_CAMPFIRE::get, true)) return true;
        if (state.getBlock() == Blocks.SOUL_CAMPFIRE && safeGet(ModConfig.ENABLE_SOUL_CAMPFIRE::get, true)) return true;
        if (state.getBlock() == Blocks.TORCH && safeGet(ModConfig.ENABLE_TORCH::get, true)) return true;
        if (state.getBlock() == Blocks.SOUL_TORCH && safeGet(ModConfig.ENABLE_SOUL_TORCH::get, true)) return true;
        if (state.getBlock() == Blocks.WALL_TORCH && safeGet(ModConfig.ENABLE_TORCH::get, true)) return true;
        if (state.getBlock() == Blocks.SOUL_WALL_TORCH && safeGet(ModConfig.ENABLE_SOUL_TORCH::get, true)) return true;
        return false;
    }

    private static boolean isLitSource(BlockState state) {
        if (!isSourceBlock(state)) return false;
        if (state.getBlock() instanceof CampfireBlock) {
            return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
        }
        return true;
    }

    /* ================= 区块区域 ================= */

    private static AABB chunkAABB(BlockPos pos, int chunkRadius) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        double minX = (double) (chunkX - chunkRadius) * CHUNK_SIZE;
        double maxX = (double) (chunkX + chunkRadius + 1) * CHUNK_SIZE;
        double minZ = (double) (chunkZ - chunkRadius) * CHUNK_SIZE;
        double maxZ = (double) (chunkZ + chunkRadius + 1) * CHUNK_SIZE;
        return new AABB(minX, -1.0e9, minZ, maxX, 1.0e9, maxZ);
    }

    /* ================= 敌对判定 ================= */

    private static boolean isHostileToPlayers(Mob mob) {
        if (mob instanceof Enemy) return true;
        if (!safeGet(ModConfig.TREAT_PROVOKED_AS_HOSTILE::get, true)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null) return false;
        if (target instanceof Player) return true;
        if (target instanceof TamableAnimal tameable && tameable.getOwner() != null) return true;
        return false;
    }

    /* ================= 主循环 ================= */

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        try {
            handle(serverLevel);
        } catch (Throwable t) {
            // 避免异常传播导致整个事件总线静默失败
            System.out.println("[TorchCampfireDamage] tick error: " + t);
            t.printStackTrace();
        }
    }

    private static void handle(ServerLevel level) {
        long gameTime = level.getGameTime();

        // 发光过期检查每 tick 执行
        tickGlowExpiry(level);

        if (gameTime % SCAN_STEP != 0) {
            return;
        }

        int chunkRadius = safeGet(ModConfig.CHUNK_RADIUS::get, 1);
        double damageAmount = safeGet(ModConfig.DAMAGE_AMOUNT::get, 2.0);
        int interval = Math.max(1, safeGet(ModConfig.DAMAGE_INTERVAL::get, 20));
        int searchBlocks = (chunkRadius + 1) * CHUNK_SIZE;

        Map<BlockPos, Long> damageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> owners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        Set<BlockPos> activePositions = new HashSet<>();
        Set<UUID> damagedThisTick = new HashSet<>();

        for (Player player : level.players()) {
            BlockPos pp = player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(
                    pp.offset(-searchBlocks, -searchBlocks, -searchBlocks),
                    pp.offset(searchBlocks, searchBlocks, searchBlocks))) {

                BlockPos ip = pos.immutable();
                if (activePositions.contains(ip)) continue;
                if (!isLitSource(level.getBlockState(ip))) continue;

                activePositions.add(ip);

                // 归属最近玩家
                UUID cur = owners.get(ip);
                Player curP = cur == null ? null : level.getPlayerByUUID(cur);
                if (curP == null
                        || player.distanceToSqr(Vec3.atCenterOf(ip)) < curP.distanceToSqr(Vec3.atCenterOf(ip))) {
                    owners.put(ip, player.getUUID());
                }

                long last = damageTimes.getOrDefault(ip, Long.MIN_VALUE / 2);
                if (gameTime - last < interval) continue;

                damageHostiles(level, ip, chunkRadius, (float) damageAmount, owners.get(ip), damagedThisTick);
                damageTimes.put(ip, gameTime);
            }
        }

        damageTimes.keySet().removeIf(p -> !isLitSource(level.getBlockState(p)));
        owners.keySet().removeIf(p -> !isLitSource(level.getBlockState(p)));
    }

    /* ================= 伤害 ================= */

    private static void damageHostiles(ServerLevel level, BlockPos center, int chunkRadius,
                                       float damage, UUID ownerUUID, Set<UUID> damagedThisTick) {
        AABB area = chunkAABB(center, chunkRadius);
        Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);
        boolean countAsKill = safeGet(ModConfig.COUNT_AS_PLAYER_KILL::get, true);
        boolean ignoreNamed = safeGet(ModConfig.IGNORE_NAMED_MOBS::get, true);
        boolean tpDrops = safeGet(ModConfig.TELEPORT_DROPS::get, true);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive() || damagedThisTick.contains(mob.getUUID())) continue;
            if (ignoreNamed && mob.hasCustomName()) continue;
            if (!isHostileToPlayers(mob)) continue;

            DamageSource src = (countAsKill && owner != null)
                    ? level.damageSources().playerAttack(owner)
                    : level.damageSources().magic();

            if (tpDrops) {
                entityDropTargets.put(mob.getUUID(), center);
            }

            applyHitEffects(mob);

            mob.hurt(src, damage);
            damagedThisTick.add(mob.getUUID());

            if (mob.isAlive()) {
                entityDropTargets.remove(mob.getUUID());
            } else {
                announceKill(level, center, mob);
            }
        }
    }

    /* ================= 命中效果 ================= */

    private static void applyHitEffects(Mob mob) {
        if (safeGet(ModConfig.GLOW_HIT_TARGETS::get, false)) {
            mob.setGlowingTag(true);
            GLOW_EXPIRY.put(mob, mob.level().getGameTime()
                    + safeGet(ModConfig.GLOW_DURATION::get, 100));
        }
        if (safeGet(ModConfig.SLOW_HIT_TARGETS::get, false)) {
            mob.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    Math.max(1, safeGet(ModConfig.SLOW_DURATION::get, 100)),
                    Math.min(254, Math.max(0, safeGet(ModConfig.SLOW_AMPLIFIER::get, 254))),
                    false, false, true));
        }
    }

    private static void tickGlowExpiry(Level level) {
        if (GLOW_EXPIRY.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Map.Entry<Mob, Long>> it = GLOW_EXPIRY.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, Long> e = it.next();
            Mob mob = e.getKey();
            if (mob == null || !mob.isAlive() || mob.level() != level) {
                it.remove();
                continue;
            }
            if (now >= e.getValue()) {
                mob.setGlowingTag(false);
                it.remove();
            }
        }
    }

    /* ================= 播报 ================= */

    private static void announceKill(ServerLevel level, BlockPos sourcePos, Mob mob) {
        if (!safeGet(ModConfig.ANNOUNCE_KILLS::get, true)) return;

        String coord = String.format("(%d, %d, %d)", sourcePos.getX(), sourcePos.getY(), sourcePos.getZ());
        String sourceName = isTorchLike(level.getBlockState(sourcePos)) ? "火把" : "篝火";

        Component msg = Component.empty()
                .append(Component.literal("🔥 [" + sourceName + " " + coord + "] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(mob.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" 被烧死了").withStyle(ChatFormatting.GRAY));

        double range = safeGet(ModConfig.ANNOUNCE_RANGE::get, 64.0);
        double rangeSq = range * range;

        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            if (sp.level() != level) continue;
            if (sp.distanceToSqr(Vec3.atCenterOf(sourcePos)) <= rangeSq) {
                sp.sendSystemMessage(msg);
            }
        }
    }

    private static boolean isTorchLike(BlockState state) {
        return state.getBlock() == Blocks.TORCH
                || state.getBlock() == Blocks.SOUL_TORCH
                || state.getBlock() == Blocks.WALL_TORCH
                || state.getBlock() == Blocks.SOUL_WALL_TORCH;
    }

    /* ================= 掉落物 ================= */

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        BlockPos target = entityDropTargets.get(entity.getUUID());
        if (target == null) return;
        if (!isLitSource(level.getBlockState(target))) return;

        for (ItemEntity item : event.getDrops()) {
            item.setPos(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
            item.setDeltaMovement(Vec3.ZERO);
        }
    }

    /* ================= 经验 ================= */

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        BlockPos target = entityDropTargets.get(entity.getUUID());
        if (target == null) return;
        if (!safeGet(ModConfig.TELEPORT_XP::get, true)) return;
        if (!isLitSource(level.getBlockState(target))) return;

        int xp = event.getDroppedExperience();
        if (xp <= 0) return;

        event.setDroppedExperience(0);
        ExperienceOrb orb = new ExperienceOrb(level,
                target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, xp);
        orb.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(orb);

        if (safeGet(ModConfig.PLAY_SOUND::get, true)) {
            level.playSound(null, target, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }
}