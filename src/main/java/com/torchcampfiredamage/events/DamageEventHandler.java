package com.torchcampfiredamage.events;

import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
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
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

public class DamageEventHandler {
    // 每 5 tick 扫描一次方块，降低性能开销
    private static final int SCAN_STEP = 5;

    // 每个维度：火源方块位置 -> 上次伤害时间
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    // 每个维度：火源方块位置 -> 归属玩家UUID
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    // 生物UUID -> 掉落物应传送到的火源位置
    private static final Map<UUID, BlockPos> entityDropTargets = new HashMap<>();

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
            // 篝火必须处于点燃状态
            return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
        }
        return true; // 火把默认点燃
    }

    /* ============ 敌对判定 ============ */

    /**
     * 是否为应当被伤害的"敌对"生物。
     * 1. 天生敌对（实现 Enemy 接口，如僵尸、骷髅、苦力怕等）→ 是
     * 2. 中立生物但已被激怒（有攻击目标 / 正在攻击玩家）→ 依据配置决定
     */
    private static boolean isHostileToPlayers(Mob mob, Level level) {
        // 天生敌对
        if (mob instanceof Enemy) {
            return true;
        }
        // 中立生物：判断是否被激怒
        if (!ModConfig.TREAT_PROVOKED_AS_HOSTILE.get()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }
        // 有攻击目标且目标是玩家（或玩家所属，如宠物攻击引发反击时目标可能不是玩家）
        if (target instanceof Player) {
            return true;
        }
        // 目标是玩家的宠物/盟友，也算作对玩家方发起攻击
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
        if (gameTime % SCAN_STEP != 0) {
            return;
        }

        final int chunkRadius = ModConfig.CHUNK_RADIUS.get();
        // 以区块为单位的检测盒（方块单位）
        final int rangeBlocks = (chunkRadius * 2 + 1) * 16;
        final double damageAmount = ModConfig.DAMAGE_AMOUNT.get();
        final int interval = Math.max(1, ModConfig.DAMAGE_INTERVAL.get());

        Map<BlockPos, Long> damageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> owners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        Set<BlockPos> activePositions = new HashSet<>();
        // 本 tick 已被伤害过的生物，避免同一只生物被多个火源重复伤害
        Set<UUID> damagedThisTick = new HashSet<>();

        for (Player player : level.players()) {
            BlockPos playerPos = player.blockPosition();

            // 只在玩家附近寻找火源方块（玩家所在区块向外扩展 chunkRadius+1 个区块）
            int searchBlocks = rangeBlocks + 16;
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

                damageHostilesInArea(serverLevel, immutablePos, rangeBlocks,
                        (float) damageAmount, owners.get(immutablePos), damagedThisTick);
                damageTimes.put(immutablePos, gameTime);
            }
        }

        // 清理失效位置的记录
        damageTimes.keySet().removeIf(pos -> !isLitSource(level.getBlockState(pos)));
        owners.keySet().removeIf(pos -> !isLitSource(level.getBlockState(pos)));
    }

    /**
     * 对以 center 为中心、半边长 half 的方形区域内的敌对生物造成伤害。
     * half 由区块半径换算而来：chunkRadius=0 → 16 格（正好覆盖所在区块），
     * chunkRadius=1 → 48 格（3x3 区块）……
     */
    private static void damageHostilesInArea(ServerLevel level, BlockPos center, int half,
                                             float damage, UUID ownerUUID, Set<UUID> damagedThisTick) {
        AABB area = AABB.ofSize(Vec3.atCenterOf(center), half * 2.0, half * 2.0, half * 2.0);

        Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (!mob.isAlive()) {
                continue;
            }
            if (damagedThisTick.contains(mob.getUUID())) {
                continue;
            }
            if (ModConfig.IGNORE_NAMED_MOBS.get() && mob.hasCustomName()) {
                continue;
            }
            if (!isHostileToPlayers(mob, level)) {
                continue;
            }

            DamageSource source;
            if (ModConfig.COUNT_AS_PLAYER_KILL.get() && owner != null) {
                source = level.damageSources().playerAttack(owner);
            } else {
                source = level.damageSources().magic();
            }

            if (ModConfig.TELEPORT_DROPS.get()) {
                entityDropTargets.put(mob.getUUID(), center);
            }

            mob.hurt(source, damage);
            damagedThisTick.add(mob.getUUID());

            // 若未死亡则清除传送标记，避免之后意外死亡时误传送
            if (mob.isAlive()) {
                entityDropTargets.remove(mob.getUUID());
            }
        }
    }

    /* ============ 掉落物传送 ============ */

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) {
            return;
        }

        BlockPos targetPos = entityDropTargets.remove(entity.getUUID());
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
}