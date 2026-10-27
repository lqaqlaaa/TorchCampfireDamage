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
 * 伤害处理器 —— 性能优化版。
 * <p>
 * 关键：不再扫描方块空间找火把，而是由 BlockEntity 自行登记到本维度的索引表中。
 * tick 时只遍历「已登记的少量火把」，无论玩家周围有多少方块，开销都与火把数量成正比。
 */
public class DamageEventHandler {
    /** 每 5 tick 处理一次伤害（可配置） */
    private static final int SCAN_STEP = 2;
    private static final int CHUNK_SIZE = SectionPos.SECTION_SIZE;

    /** 维度 -> 已登记火把位置集合 */
    private static final Map<Level, Set<BlockPos>> REGISTERED = new WeakHashMap<>();
    /** 火把位置 -> 上次伤害时间 */
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    /** 火把位置 -> 归属玩家UUID */
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    /** 生物UUID -> 传送目标 */
    private static final Map<UUID, DropTarget> entityDropTargets = new HashMap<>();
    /** 发光中的生物 -> 到期时间 */
    private static final Map<Mob, Long> GLOW_EXPIRY = new WeakHashMap<>();

    private static boolean DEBUG_PRINTED = false;

    public record DropTarget(BlockPos pos, boolean drops, boolean xp) {}

    /* ================= 登记接口（由 BlockEntity 调用） ================= */

    public static void register(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        REGISTERED.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
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
        System.out.println("[TorchCampfireDamage] Server started. Ward torches ready (indexed mode).");
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
            return new AABB(minX, -1.0e9, minZ, maxX, 1.0e9, maxZ);
        } else {
            double r = ward.getBlockRadius() + 0.5;
            return AABB.ofSize(Vec3.atCenterOf(center), r * 2, r * 2, r * 2);
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
        try {
            handle(serverLevel);
        } catch (Throwable t) {
            System.out.println("[TorchCampfireDamage] tick error: " + t);
            t.printStackTrace();
        }
    }

    private static void handle(ServerLevel level) {
        // 发光过期检查每 tick 执行（几乎零成本）
        tickGlowExpiry(level);

        Set<BlockPos> torches = REGISTERED.get(level);
        if (torches == null || torches.isEmpty()) {
            return;   // 没有火把 → 立即返回，零开销
        }

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
        // 遍历副本，避免 removeIf 时的并发修改
        List<BlockPos> snapshot = new ArrayList<>(torches);

        // 只处理「附近有玩家」的火把（避免加载远处区块实体）
        List<? extends Player> players = level.players();

        for (BlockPos ip : snapshot) {
            BlockEntity be = level.getBlockEntity(ip);
            if (!(be instanceof WardSourceBlockEntity ward)) {
                // 方块已被移除 → 从索引中清除
                unregister(level, ip);
                continue;
            }
            if (!ward.isEnabled()) continue;

            // 找最近的玩家；若附近没有玩家则不处理（省性能）
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

            // 距离超过「最大影响范围 + 128」就跳过
            int maxRangeBlocks = (ward.isChunkMode() ? (ward.getChunkRadius() + 1) * 16 : ward.getBlockRadius() + 16);
            if (nearestSq > (double) (maxRangeBlocks + 128) * (maxRangeBlocks + 128)) {
                continue;
            }
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

            if (tpDrops || tpXp) {
                entityDropTargets.put(mob.getUUID(), new DropTarget(center, tpDrops, tpXp));
            }

            boolean hurtOk = mob.hurt(src, damage);
            if (!hurtOk) {
                mob.invulnerableTime = 0;
                float nh = mob.getHealth() - damage;
                mob.setHealth(Math.max(0.0f, nh));
                if (mob.getHealth() <= 0.0f) {
                    mob.die(src);
                }
            }

            damagedThisTick.add(mob.getUUID());

            if (mob.isAlive()) {
                entityDropTargets.remove(mob.getUUID());
            } else if (announce) {
                announceKill(level, center, mob);
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

    /* ================= 掉落物 ================= */

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

    /* ================= 经验 ================= */

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        DropTarget target = entityDropTargets.remove(entity.getUUID());
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