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
    // 每个维度：篝火/火把位置 -> 上次伤害时间
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    // 每个维度：位置 -> 归属玩家UUID
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    // 生物UUID -> 掉落物应传送到的位置
    private static final Map<UUID, BlockPos> entityDropTargets = new HashMap<>();

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
        // 篝火需要是点燃状态
        if (state.getBlock() instanceof CampfireBlock) {
            return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
        }
        return true; // 火把默认就是点燃的
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // 每 5 tick 扫描一次，降低性能开销
        if (level.getGameTime() % 5 != 0) {
            return;
        }

        final int radius = ModConfig.DAMAGE_RADIUS.get();
        final float damageAmount = ModConfig.DAMAGE_AMOUNT.get().floatValue();
        final int interval = ModConfig.DAMAGE_INTERVAL.get();
        final long currentTime = level.getGameTime();

        Map<BlockPos, Long> damageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> owners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        Set<BlockPos> activePositions = new HashSet<>();

        for (Player player : level.players()) {
            BlockPos playerPos = player.blockPosition();

            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-radius, -radius, -radius),
                    playerPos.offset(radius, radius, radius))) {

                BlockPos immutablePos = pos.immutable();
                if (activePositions.contains(immutablePos)) {
                    continue;
                }
                if (!isLitSource(level.getBlockState(immutablePos))) {
                    continue;
                }

                activePositions.add(immutablePos);

                // 更新归属：取距离最近的玩家
                UUID currentOwner = owners.get(immutablePos);
                Player currentOwnerPlayer = currentOwner == null ? null : level.getPlayerByUUID(currentOwner);
                if (currentOwnerPlayer == null
                        || player.distanceToSqr(Vec3.atCenterOf(immutablePos))
                        < currentOwnerPlayer.distanceToSqr(Vec3.atCenterOf(immutablePos))) {
                    owners.put(immutablePos, player.getUUID());
                }

                long lastTime = damageTimes.getOrDefault(immutablePos, -interval);
                if (currentTime - lastTime < interval) {
                    continue;
                }

                damageHostilesInRange(serverLevel, immutablePos, radius, damageAmount, owners.get(immutablePos));
                damageTimes.put(immutablePos, currentTime);
            }
        }

        // 清理失效位置的记录
        damageTimes.keySet().removeIf(pos -> !isLitSource(level.getBlockState(pos)));
        owners.keySet().removeIf(pos -> !isLitSource(level.getBlockState(pos)));
    }

    private static void damageHostilesInRange(ServerLevel level, BlockPos center, int radius, float damage, UUID ownerUUID) {
        AABB area = new AABB(center).inflate(radius);

        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            // 只处理敌对生物（实现 Enemy 接口的怪物）
            if (!(mob instanceof Enemy)) {
                continue;
            }
            if (!mob.isAlive()) {
                continue;
            }
            if (ModConfig.IGNORE_NAMED_MOBS.get() && mob.hasCustomName()) {
                continue;
            }

            Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);
            DamageSource source;
            if (ModConfig.COUNT_AS_PLAYER_KILL.get() && owner != null) {
                source = level.damageSources().playerAttack(owner);
            } else {
                source = level.damageSources().magic();
            }

            // 记录该生物死亡后掉落物要传送到的位置
            if (ModConfig.TELEPORT_DROPS.get()) {
                entityDropTargets.put(mob.getUUID(), center);
            }

            mob.hurt(source, damage);

            // 如果生物没死，移除传送记录，避免误传送正常死亡
            if (mob.isAlive() && !entityDropTargets.containsKey(mob.getUUID())) {
                entityDropTargets.remove(mob.getUUID());
            }
        }
    }

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

        // 目标方块仍存在且有效才传送，否则掉落物留在原地
        if (!isLitSource(level.getBlockState(targetPos))) {
            return;
        }

        for (ItemEntity itemEntity : event.getDrops()) {
            itemEntity.setPos(targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5);
            itemEntity.setDeltaMovement(Vec3.ZERO);
        }
    }
}