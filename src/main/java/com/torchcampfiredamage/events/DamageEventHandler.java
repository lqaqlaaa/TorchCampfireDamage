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
import com.torchcampfiredamage.block.WardCampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    /** 最大搜索半径：9x9 区块 → 5 区块半径 */
    private static final int MAX_SEARCH_CHUNKS = 4;

    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    private static final Map<UUID, BlockPos> entityDropTargets = new HashMap<>();
    private static final Map<Mob, Long> GLOW_EXPIRY = new WeakHashMap<>();

    private static boolean DEBUG_PRINTED = false;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        System.out.println("[TorchCampfireDamage] Server started. Ward blocks registered.");
    }

    private static <T> T safeGet(java.util.function.Supplier<T> supplier, T def) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return def;
        }
    }

    /* ================= 区域 ================= */

    private static AABB chunkAABB(BlockPos pos, int chunkRadius) {
        int cx = SectionPos.blockToSectionCoord(pos.getX());
        int cz = SectionPos.blockToSectionCoord(pos.getZ());
        double minX = (double) (cx - chunkRadius) * CHUNK_SIZE;
        double maxX = (double) (cx + chunkRadius + 1) * CHUNK_SIZE;
        double minZ = (double) (cz - chunkRadius) * CHUNK_SIZE;
        double maxZ = (double) (cz + chunkRadius + 1) * CHUNK_SIZE;
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
        long gameTime = level.getGameTime();
        tickGlowExpiry(level);

        if (gameTime % SCAN_STEP != 0) {
            return;
        }

        if (!DEBUG_PRINTED) {
            DEBUG_PRINTED = true;
            System.out.println("[TorchCampfireDamage] DEBUG tick running. players=" + level.players().size());
        }

        int searchBlocks = (MAX_SEARCH_CHUNKS + 1) * CHUNK_SIZE;

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

                BlockEntity be = level.getBlockEntity(ip);
                if (!(be instanceof WardSourceBlockEntity ward)) continue;
                if (!ward.isEnabled()) continue;

                // 篝火需要点燃
                BlockState state = level.getBlockState(ip);
                if (state.getBlock() instanceof WardCampfireBlock
                        && state.hasProperty(WardCampfireBlock.LIT)
                        && !state.getValue(WardCampfireBlock.LIT)) {
                    continue;
                }

                activePositions.add(ip);

                int chunkRadius = ward.getChunkRadius();
                double damage = ward.getDamage();
                int interval = Math.max(1, ward.getInterval());
                boolean announce = ward.isAnnounce();
                boolean glowing = ward.isGlowing();
                boolean slow = ward.isSlow();
                int slowAmp = ward.getSlowAmplifier();

                // 归属最近玩家
                UUID cur = owners.get(ip);
                Player curP = cur == null ? null : level.getPlayerByUUID(cur);
                if (curP == null
                        || player.distanceToSqr(Vec3.atCenterOf(ip)) < curP.distanceToSqr(Vec3.atCenterOf(ip))) {
                    owners.put(ip, player.getUUID());
                }

                long last = damageTimes.getOrDefault(ip, Long.MIN_VALUE / 2);
                if (gameTime - last < interval) continue;

                damageHostiles(level, ip, chunkRadius, (float) damage, owners.get(ip),
                        damagedThisTick, announce, glowing, slow, slowAmp);
                damageTimes.put(ip, gameTime);
            }
        }

        // 清理失效位置
        damageTimes.keySet().removeIf(p -> !(level.getBlockEntity(p) instanceof WardSourceBlockEntity));
        owners.keySet().removeIf(p -> !(level.getBlockEntity(p) instanceof WardSourceBlockEntity));
    }

    /* ================= 伤害（含强制扣血） ================= */

    private static void damageHostiles(ServerLevel level, BlockPos center, int chunkRadius,
                                       float damage, UUID ownerUUID, Set<UUID> damagedThisTick,
                                       boolean announce, boolean glowing, boolean slow, int slowAmp) {
        AABB area = chunkAABB(center, chunkRadius);
        Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);
        boolean countAsKill = safeGet(ModConfig.COUNT_AS_PLAYER_KILL::get, true);
        boolean ignoreNamed = safeGet(ModConfig.IGNORE_NAMED_MOBS::get, true);
        boolean tpDrops = safeGet(ModConfig.TELEPORT_DROPS::get, true);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive() || damagedThisTick.contains(mob.getUUID())) continue;
            if (ignoreNamed && mob.hasCustomName()) continue;
            if (!isHostileToPlayers(mob)) continue;

            // 标记/减速（伤害为 0 时也生效）
            if (glowing) {
                mob.setGlowingTag(true);
                GLOW_EXPIRY.put(mob, level.getGameTime() + safeGet(ModConfig.GLOW_DURATION::get, 100));
            }
            if (slow) {
                mob.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        Math.max(1, safeGet(ModConfig.SLOW_DURATION::get, 100)),
                        Math.min(254, Math.max(0, slowAmp)),
                        false, false, true));
            }

            if (damage <= 0) continue;

            DamageSource src = (countAsKill && owner != null)
                    ? level.damageSources().playerAttack(owner)
                    : level.damageSources().magic();

            if (tpDrops) {
                entityDropTargets.put(mob.getUUID(), center);
            }

            boolean hurtOk = mob.hurt(src, damage);

            // 强制扣血：未造成伤害 或 血量没变化（被无敌帧/免疫/抗性挡下）
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
        BlockState st = level.getBlockState(sourcePos);
        String sourceName = (st.getBlock() instanceof WardCampfireBlock) ? "守卫篝火" : "守卫火把";

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

    /* ================= 掉落物 ================= */

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        BlockPos target = entityDropTargets.get(entity.getUUID());
        if (target == null) return;
        if (!(level.getBlockEntity(target) instanceof WardSourceBlockEntity)) return;

        for (ItemEntity item : event.getDrops()) {
            item.setPos(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
            item.setDeltaMovement(Vec3.ZERO);
        }
    }

    /* ================= 经验 ================= */

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        BlockPos target = entityDropTargets.get(entity.getUUID());
        if (target == null) return;
        if (!safeGet(ModConfig.TELEPORT_XP::get, true)) return;
        if (!(level.getBlockEntity(target) instanceof WardSourceBlockEntity)) return;

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