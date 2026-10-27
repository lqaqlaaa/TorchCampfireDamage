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
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

public class DamageEventHandler {
    /** 每 5 tick 扫描一次方块，降低性能开销 */
    private static final int SCAN_STEP = 5;
    /** 区块边长 */
    private static final int CHUNK_SIZE = SectionPos.SECTION_SIZE; // 16

    /** 每个维度：火源方块位置 -> 上次伤害时间 */
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    /** 每个维度：火源方块位置 -> 归属玩家UUID */
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    /** 生物UUID -> 掉落物/经验应传送到的火源位置 */
    private static final Map<UUID, BlockPos> entityDropTargets = new HashMap<>();
    /** 发光中的生物 UUID -> 到期游戏时间 */
    private static final Map<UUID, Long> GLOW_EXPIRY = new HashMap<>();
    /** 需要检查发光过期的 UUID 集合 */
    private static final Set<UUID> GLOW_TRACKED = new HashSet<>();

    /* ============ 火源判定 ============ */

    private static boolean isSourceBlock(BlockState state) {
        if (state.isAir()) return false;
        if (state.getBlock() == Blocks.CAMPFIRE && ModConfig.ENABLE_CAMPFIRE.get()) return true;
        if (state.getBlock() == Blocks.SOUL_CAMPFIRE && ModConfig.ENABLE_SOUL_CAMPFIRE.get()) return true;
        if (state.getBlock() == Blocks.TORCH && ModConfig.ENABLE_TORCH.get()) return true;
        if (state.getBlock() == Blocks.SOUL_TORCH && ModConfig.ENABLE_SOUL_TORCH.get()) return true;
        if (state.getBlock() == Blocks.WALL_TORCH && ModConfig.ENABLE_TORCH.get()) return true;
        if (state.getBlock() == Blocks.SOUL_WALL_TORCH && ModConfig.ENABLE_SOUL_TORCH.get()) return true;
        return false;
    }

    private static boolean isLitSource(BlockState state) {
        if (!isSourceBlock(state)) return false;
        if (state.getBlock() instanceof CampfireBlock) {
            return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
        }
        return true; // 火把默认点燃
    }

    /* ============ 区块范围计算 ============ */

    /**
     * 根据区块半径计算以 pos 所在区块为中心的方形区域（方块坐标范围）。
     * chunkRadius = 1 → 中心区块 + 周围一圈 = 3x3 区块
     * chunkRadius = 2 → 5x5 区块
     * <p>
     * 返回 {@code [minX, minY, minZ, maxX, maxY, maxZ]}（Y 轴不限，整列生效）。
     */
    private static int[] chunkAreaOf(BlockPos pos, int chunkRadius) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());

        int minX = (chunkX - chunkRadius) * CHUNK_SIZE;
        int maxX = (chunkX + chunkRadius + 1) * CHUNK_SIZE; // 区块右下角（不含）
        int minZ = (chunkZ - chunkRadius) * CHUNK_SIZE;
        int maxZ = (chunkZ + chunkRadius + 1) * CHUNK_SIZE;

        return new int[]{minX, Integer.MIN_VALUE, minZ, maxX, Integer.MAX_VALUE, maxZ};
    }

    private static AABB chunkAABB(BlockPos pos, int chunkRadius) {
        int[] a = chunkAreaOf(pos, chunkRadius);
        return new AABB(a[0], a[1], a[2], a[3], a[4], a[5]);
    }

    /* ============ 敌对判定 ============ */

    /**
     * 是否为应当被伤害的"敌对"生物。
     * 1. 天生敌对（实现 Enemy 接口）→ 是
     * 2. 中立生物但已被激怒（攻击目标为玩家或其所属生物）→ 依据配置
     */
    private static boolean isHostileToPlayers(Mob mob) {
        if (mob instanceof Enemy) {
            return true;
        }
        if (!ModConfig.TREAT_PROVOKED_AS_HOSTILE.get()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }
        if (target instanceof Player) {
            return true;
        }
        // 目标是玩家的宠物/驯服动物（如狼、猫），也视作对玩家方发起攻击
        if (target instanceof net.minecraft.world.entity.TamableAnimal tameable && tameable.getOwner() != null) {
            return true;
        }
        return false;
    }

    /* ============ 主循环 ============ */

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        final long gameTime = level.getGameTime();

        // 发光过期检查必须每 tick 执行，不受扫描节流影响
        tickGlowExpiry(level);

        if (gameTime % SCAN_STEP != 0) {
            return;
        }

        final int chunkRadius = ModConfig.CHUNK_RADIUS.get();
        // 寻找火源方块的搜索范围：以玩家为中心，区块半径 + 1 圈区块
        final int searchBlocks = (chunkRadius + 1) * CHUNK_SIZE;
        final double damageAmount = ModConfig.DAMAGE_AMOUNT.get();
        final int interval = Math.max(1, ModConfig.DAMAGE_INTERVAL.get());

        Map<BlockPos, Long> damageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> owners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        Set<BlockPos> activePositions = new HashSet<>();
        // 本 tick 已被伤害过的生物，避免多个火源重叠时重复伤害
        Set<UUID> damagedThisTick = new HashSet<>();

        for (Player player : level.players()) {
            BlockPos playerPos = player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-searchBlocks, -searchBlocks, -searchBlocks),
                    playerPos.offset(searchBlocks, searchBlocks, searchBlocks))) {

                BlockPos immutablePos = pos.immutable();
                if (activePositions.contains(immutablePos)) {
                    continue;
                }
                if (!isLitSource(level.getBlockState(immutablePos))) {
                    continue;
                }

                activePositions.add(immutablePos);

                // 归属：距离最近的玩家
                UUID currentOwner = owners.get(immutablePos);
                Player currentOwnerPlayer = currentOwner == null ? null : level.getPlayerByUUID(currentOwner);
                if (currentOwnerPlayer == null
                        || player.distanceToSqr(Vec3.atCenterOf(immutablePos))
                        < currentOwnerPlayer.distanceToSqr(Vec3.atCenterOf(immutablePos))) {
                    owners.put(immutablePos, player.getUUID());
                }

                long lastTime = damageTimes.getOrDefault(immutablePos, Long.MIN_VALUE / 2);
                if (gameTime - lastTime < interval) {
                    continue;
                }

                damageHostilesInArea(serverLevel, immutablePos, chunkRadius,
                        (float) damageAmount, owners.get(immutablePos), damagedThisTick);
                damageTimes.put(immutablePos, gameTime);
            }
        }

        // 清理失效位置的记录
        damageTimes.keySet().removeIf(pos -> !isLitSource(level.getBlockState(pos)));
        owners.keySet().removeIf(pos -> !isLitSource(level.getBlockState(pos)));
    }

    /* ============ 造成伤害 ============ */

    private static void damageHostilesInArea(ServerLevel level, BlockPos center, int chunkRadius,
                                             float damage, UUID ownerUUID, Set<UUID> damagedThisTick) {
        AABB area = chunkAABB(center, chunkRadius);
        Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive() || damagedThisTick.contains(mob.getUUID())) {
                continue;
            }
            if (ModConfig.IGNORE_NAMED_MOBS.get() && mob.hasCustomName()) {
                continue;
            }
            if (!isHostileToPlayers(mob)) {
                continue;
            }

            DamageSource source = (ModConfig.COUNT_AS_PLAYER_KILL.get() && owner != null)
                    ? level.damageSources().playerAttack(owner)
                    : level.damageSources().magic();

            if (ModConfig.TELEPORT_DROPS.get()) {
                entityDropTargets.put(mob.getUUID(), center);
            }

            // 命中附加效果：发光 / 减速
            applyHitEffects(mob);

            mob.hurt(source, damage);
            damagedThisTick.add(mob.getUUID());

            if (mob.isAlive()) {
                // 未死亡，清除传送标记，避免之后意外死亡时误传送
                entityDropTargets.remove(mob.getUUID());
            } else {
                // 死亡：播报击杀信息
                announceKill(level, center, mob, owner);
            }
        }
    }

    /* ============ 命中附加效果 ============ */

    /**
     * 给被命中的生物施加发光 / 减速效果。
     * 效果持续时间内每次命中都会刷新时限。
     */
    private static void applyHitEffects(Mob mob) {
        if (ModConfig.GLOW_HIT_TARGETS.get()) {
            mob.setGlowingTag(true);
            // 用自定义计时器控制发光结束
            GLOW_EXPIRY.put(mob.getUUID(), mob.level().getGameTime() + ModConfig.GLOW_DURATION.get());
            GLOW_TRACKED.add(mob.getUUID());
        }

        if (ModConfig.SLOW_HIT_TARGETS.get()) {
            mob.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    ModConfig.SLOW_DURATION.get(),
                    ModConfig.SLOW_AMPLIFIER.get(),
                    false,  // ambient
                    false,  // 不显示粒子
                    true    // 显示图标
            ));
        }
    }

    /** 检查并移除已过期的发光标记 */
    public static void tickGlowExpiry(Level level) {
        if (GLOW_TRACKED.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<UUID> it = GLOW_TRACKED.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Long expiry = GLOW_EXPIRY.get(id);
            if (expiry == null || now >= expiry) {
                if (level.getEntity(id) instanceof Mob mob) {
                    mob.setGlowingTag(false);
                }
                GLOW_EXPIRY.remove(id);
                it.remove();
            }
        }
    }

    /* ============ 击杀播报 ============ */

    private static void announceKill(ServerLevel level, BlockPos sourcePos, Mob mob, Player owner) {
        if (!ModConfig.ANNOUNCE_KILLS.get()) {
            return;
        }

        String coord = String.format("(%d, %d, %d)", sourcePos.getX(), sourcePos.getY(), sourcePos.getZ());
        String sourceName = isTorchLike(level.getBlockState(sourcePos)) ? "火把" : "篝火";
        String mobName = mob.getName().getString();

        Component msg = Component.empty()
                .append(Component.literal("🔥 [" + sourceName + " " + coord + "] ")
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(mobName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" 被烧死了").withStyle(ChatFormatting.GRAY));

        // 播报给附近玩家（配置的半径内），默认全服可见的范围内
        double range = ModConfig.ANNOUNCE_RANGE.get();
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

    /* ============ 掉落物传送 ============ */

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }

        BlockPos targetPos = entityDropTargets.get(entity.getUUID());
        if (targetPos == null) {
            return;
        }
        if (!isLitSource(level.getBlockState(targetPos))) {
            return; // 火源已被拆除，掉落物留在原地
        }

        for (ItemEntity itemEntity : event.getDrops()) {
            itemEntity.setPos(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
            itemEntity.setDeltaMovement(Vec3.ZERO);
        }
    }

    /* ============ 经验传送 ============ */

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }

        BlockPos targetPos = entityDropTargets.get(entity.getUUID());
        if (targetPos == null) {
            return;
        }
        if (!ModConfig.TELEPORT_XP.get()) {
            return;
        }
        if (!isLitSource(level.getBlockState(targetPos))) {
            return;
        }

        int xp = event.getDroppedExperience();
        if (xp <= 0) {
            return;
        }

        // 取消原本的经验球掉落，改为在火源处生成
        event.setDroppedExperience(0);

        ExperienceOrb orb = new ExperienceOrb(level,
                targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5, xp);
        orb.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(orb);

        // 播放吸取音效，方便感知
        if (ModConfig.PLAY_SOUND.get()) {
            level.playSound(null, targetPos, SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.4f, 1.6f);
        }
    }

    /* ============ 清理 ============ */

    /**
     * 生物死亡后清掉传送标记，避免 UUID 长期驻留内存。
     * LivingDropsEvent 与 LivingExperienceDropEvent 都会读取它，
     * 所以只在两者都跑完后清理——这里用 LivingDeathEvent 之后的一 tick 清理更稳妥，
     * 但简单起见依赖 Drops/XP 事件本身的读取，然后由 tick 定期扫除不存在实体的记录。
     */
    public static void cleanupTargets(ServerLevel level) {
        if (entityDropTargets.isEmpty()) {
            return;
        }
        entityDropTargets.entrySet().removeIf(e -> level.getEntity(e.getKey()) == null);
    }
}