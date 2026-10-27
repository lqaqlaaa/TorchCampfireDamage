package com.torchcampfiredamage.events;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/**
 * 伤害处理器 —— 索引登记模式（性能友好）。
 * <p>
 * 由 BlockEntity 在加载/卸载时调用 register/unregister，
 * tick 时只遍历已登记的火把，与地图大小无关。
 */
public class DamageEventHandler {
    private static final int SCAN_STEP = 2;
    private static final int CHUNK_SIZE = SectionPos.SECTION_SIZE;

    private static final Map<Level, Set<BlockPos>> REGISTERED = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();

    /** 生物UUID -> 传送目标（掉落物 / 经验） */
    private static final Map<UUID, DropTarget> entityDropTargets = new HashMap<>();
    /** 已死亡、待清理的标记（延迟一 tick 清理，确保两个事件都跑完） */
    private static final Map<UUID, Integer> pendingCleanup = new HashMap<>();

    private static final Map<Mob, Long> GLOW_EXPIRY = new WeakHashMap<>();
    private static boolean DEBUG_PRINTED = false;

    public record DropTarget(BlockPos pos, boolean drops, boolean xp) {}

    /* ================= 登记接口 ================= */

    public static void register(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Set<BlockPos> set = REGISTERED.get(level);
        if (set == null) {
            set = new HashSet<>();
            REGISTERED.put(level, set);
        }
        // BlockPos 已由调用方保证不可变，避免重复生成对象
        if (set.size() > 4096) {
            set.clear();   // 防御：异常膨胀时重置，下次 tick 会重新登记
        }
        set.add(pos);
    }

    public static void unregister(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Set<BlockPos> set = REGISTERED.get(level);
        if (set != null) set.remove(pos);
        Map<BlockPos, Long> t = lastDamageTimes.get(level);
        if (t != null) t.remove(pos);
        Map<BlockPos, UUID> o = positionOwners.get(level);
        if (o != null) o.remove(pos);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        System.out.println("[TorchCampfireDamage] Server started. Ward torches ready (indexed).");
    }

    private static <T> T safeGet(java.util.function.Supplier<T> supplier, T def) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return def;
        }
    }

    /* ================= 区域 ================= */

    private static AABB damageArea(BlockPos center, WardSourceBlockEntity ward) {
        if (ward.isChunkMode()) {
            int r = ward.getChunkRadius();
            int cx = SectionPos.blockToSectionCoord(center.getX());
            int cz = SectionPos.blockToSectionCoord(center.getZ());
            double minX = (double) (cx - r) * CHUNK_SIZE;
            double maxX = (double) (cx + r + 1) * CHUNK_SIZE;
            double minZ = (double) (cz - r) * CHUNK_SIZE;
            double maxZ = (double) (cz + r + 1) * CHUNK_SIZE;
            // Y 轴只取 128 格（原版怪物活动/巡逻范围级别），避免大范围实体查询
            double y = center.getY();
            return new AABB(minX, y - 64, minZ, maxX, y + 64, maxZ);
        } else {
            double r = ward.getBlockRadius() + 0.5;
            double y = Math.min(r, 64);
            return AABB.ofSize(Vec3.atCenterOf(center), r * 2, y * 2, r * 2);
        }
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
    public void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long t0 = System.nanoTime();
        try {
            handle(serverLevel);
        } catch (Throwable t) {
            System.out.println("[TorchCampfireDamage] tick error: " + t);
            t.printStackTrace();
        }
        // 性能诊断：每 100 tick 报告一次耗时
        long used = System.nanoTime() - t0;
        PROF_TOTAL_NS += used;
        PROF_TICKS++;
        if (PROF_TICKS >= 100) {
            long avgUs = PROF_TOTAL_NS / PROF_TICKS / 1000;
            if (avgUs > 50) {   // 超过 50 微秒才报告（正常应 <5µs）
                System.out.println("[TorchCampfireDamage][PERF] avg=" + avgUs + "us/tick"
                        + " torches=" + (REGISTERED.get(serverLevel) == null ? 0 : REGISTERED.get(serverLevel).size())
                        + " glow=" + GLOW_EXPIRY.size()
                        + " pending=" + pendingCleanup.size());
            }
            PROF_TOTAL_NS = 0;
            PROF_TICKS = 0;
        }
    }

    private static long PROF_TOTAL_NS = 0;
    private static int PROF_TICKS = 0;

    private static void handle(ServerLevel level) {
        // 极高性价比：没有火把时立刻返回，不碰任何 Map
        Set<BlockPos> torches = REGISTERED.get(level);
        if (torches == null || torches.isEmpty()) {
            // 仅当有过期发光/待清理项时才做清理
            if (!GLOW_EXPIRY.isEmpty()) tickGlowExpiry(level);
            if (!pendingCleanup.isEmpty()) cleanPending(level);
            return;
        }

        if (!GLOW_EXPIRY.isEmpty()) tickGlowExpiry(level);
        if (!pendingCleanup.isEmpty()) cleanPending(level);

        long gameTime = level.getGameTime();
        if (gameTime % SCAN_STEP != 0) {
            return;
        }

        if (!DEBUG_PRINTED) {
            DEBUG_PRINTED = true;
            System.out.println("[TorchCampfireDamage] indexed tick active, torches=" + torches.size());
        }

        Map<BlockPos, Long> damageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> owners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        Set<UUID> damagedThisTick = new HashSet<>();
        List<BlockPos> snapshot = new ArrayList<>(torches);
        List<? extends Player> players = level.players();

        for (BlockPos ip : snapshot) {
            BlockEntity be = level.getBlockEntity(ip);
            if (!(be instanceof WardSourceBlockEntity ward)) {
                unregister(level, ip);
                continue;
            }
            if (!ward.isEnabled()) continue;

            // 找最近玩家
            Player nearest = null;
            double nearestSq = Double.MAX_VALUE;
            for (Player p : players) {
                double d = p.distanceToSqr(Vec3.atCenterOf(ip));
                if (d < nearestSq) {
                    nearestSq = d;
                    nearest = p;
                }
            }
            if (nearest == null) continue;

            int maxRange = (ward.isChunkMode() ? (ward.getChunkRadius() + 1) * 16 : ward.getBlockRadius() + 16);
            double limit = (double) (maxRange + 128) * (maxRange + 128);
            if (nearestSq > limit) continue;

            owners.put(ip, nearest.getUUID());

            int interval = Math.max(1, ward.getInterval());
            long last = damageTimes.getOrDefault(ip, Long.MIN_VALUE / 2);
            if (gameTime - last < interval) continue;

            damageHostiles(level, ip, ward, owners.get(ip), damagedThisTick);
            damageTimes.put(ip, gameTime);
        }
    }

    /* ================= 伤害 ================= */

    private static void damageHostiles(ServerLevel level, BlockPos center, WardSourceBlockEntity ward,
                                       UUID ownerUUID, Set<UUID> damagedThisTick) {
        AABB area = damageArea(center, ward);
        Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);

        float damage = (float) ward.getDamage();
        boolean glowing = ward.isGlowing();
        boolean slow = ward.isSlow();
        boolean announce = ward.isAnnounce();
        boolean tpDrops = ward.isTeleportDrops();
        boolean tpXp = ward.isTeleportXp();

        boolean countAsKill = safeGet(ModConfig.COUNT_AS_PLAYER_KILL::get, true);
        boolean ignoreNamed = safeGet(ModConfig.IGNORE_NAMED_MOBS::get, true);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive() || damagedThisTick.contains(mob.getUUID())) continue;
            if (ignoreNamed && mob.hasCustomName()) continue;
            if (!isHostileToPlayers(mob)) continue;

            // 附加效果
            if (glowing) {
                mob.setGlowingTag(true);
                GLOW_EXPIRY.put(mob, level.getGameTime() + safeGet(ModConfig.GLOW_DURATION::get, 100));
            }
            if (slow) {
                mob.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        Math.max(1, safeGet(ModConfig.SLOW_DURATION::get, 100)),
                        Math.min(254, Math.max(0, ward.getSlowAmplifier())),
                        false, false, true));
            }

            if (damage <= 0) {
                damagedThisTick.add(mob.getUUID());
                continue;
            }

            DamageSource src = (countAsKill && owner != null)
                    ? level.damageSources().playerAttack(owner)
                    : level.damageSources().magic();

            // 先登记传送目标，再打伤害
            if (tpDrops || tpXp) {
                entityDropTargets.put(mob.getUUID(), new DropTarget(center, tpDrops, tpXp));
            }

            boolean hurtOk = mob.hurt(src, damage);
            if (!hurtOk) {
                // 强制扣血（穿透无敌帧 / 免疫）
                mob.invulnerableTime = 0;
                float nh = mob.getHealth() - damage;
                mob.setHealth(Math.max(0.0f, nh));
                if (mob.getHealth() <= 0.0f) {
                    mob.die(src);
                }
            }

            damagedThisTick.add(mob.getUUID());

            if (!mob.isAlive()) {
                if (announce) {
                    announceKill(level, center, mob);
                }
                // 延迟清理：让 LivingDropsEvent / LivingExperienceDropEvent 有机会读取
                pendingCleanup.put(mob.getUUID(), 80);
            } else {
                // 活着 → 撤销传送标记
                entityDropTargets.remove(mob.getUUID());
            }
        }
    }

    /** 每 tick 递减待清理计数器，超时后强制清除残留标记 */
    private static void cleanPending(ServerLevel level) {
        if (pendingCleanup.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = pendingCleanup.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left <= 0) {
                entityDropTargets.remove(e.getKey());
                it.remove();
            } else {
                e.setValue(left);
            }
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
        if (!safeGet(ModConfig.GLOBAL_ANNOUNCE_ENABLED::get, true)) return;

        String coord = String.format("(%d, %d, %d)", sourcePos.getX(), sourcePos.getY(), sourcePos.getZ());
        String mobName = mob.getName().getString();

        Component msg = Component.empty()
                .append(Component.literal("[守卫火把 " + coord + "] ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(mobName).withStyle(ChatFormatting.YELLOW))
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

    /* ================= 掉落物传送 ================= */

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        DropTarget target = entityDropTargets.get(entity.getUUID());
        if (target == null || !target.drops()) return;

        BlockPos tp = target.pos();
        for (ItemEntity item : event.getDrops()) {
            item.setPos(tp.getX() + 0.5, tp.getY() + 1.0, tp.getZ() + 0.5);
            item.setDeltaMovement(Vec3.ZERO);
        }
    }

    /* ================= 经验传送 ================= */

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        DropTarget target = entityDropTargets.get(entity.getUUID());
        if (target == null || !target.xp()) return;
        if (!safeGet(ModConfig.TELEPORT_XP::get, true)) return;

        int xp = event.getDroppedExperience();
        if (xp <= 0) return;

        BlockPos tp = target.pos();
        event.setDroppedExperience(0);

        ExperienceOrb orb = new ExperienceOrb(level,
                tp.getX() + 0.5, tp.getY() + 1.0, tp.getZ() + 0.5, xp);
        orb.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(orb);

        if (safeGet(ModConfig.PLAY_SOUND::get, true)) {
            level.playSound(null, tp, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }
}