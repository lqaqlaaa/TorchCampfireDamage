package com.torchcampfiredamage.events;

import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

public class DamageEventHandler {
    // 存储每个维度的篝火/火把位置及其上次伤害时间
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new WeakHashMap<>();
    
    // 存储每个位置归属的玩家（用于判定击杀归属）
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new WeakHashMap<>();
    
    // 存储被标记的生物UUID -> 目标位置（用于掉落物传送）
    private static final Map<UUID, BlockPos> entityDropTargets = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 每5tick扫描一次，减少性能消耗
        if (level.getGameTime() % 5 != 0) {
            return;
        }

        int radius = ModConfig.DAMAGE_RADIUS.get();
        double damageAmount = ModConfig.DAMAGE_AMOUNT.get();
        int interval = ModConfig.DAMAGE_INTERVAL.get();
        long currentTime = level.getGameTime();

        // 获取或创建该维度的记录
        Map<BlockPos, Long> levelDamageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> levelOwners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        // 扫描所有玩家周围的篝火和火把
        Set<BlockPos> processedPositions = new HashSet<>();
        
        for (Player player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            
            // 扫描玩家周围半径内的篝火和火把
            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-radius, -radius, -radius),
                    playerPos.offset(radius, radius, radius))) {
                
                if (processedPositions.contains(pos)) {
                    continue;
                }
                
                BlockState state = level.getBlockState(pos);
                if (!isValidBlock(state)) {
                    continue;
                }
                
                // 检查是否点燃（对于篝火）
                if (state.getBlock() instanceof CampfireBlock) {
                    if (!state.getValue(CampfireBlock.LIT)) {
                        continue;
                    }
                }
                
                processedPositions.add(pos.immutable());
                
                // 记录归属玩家（最近的玩家）
                if (!levelOwners.containsKey(pos)) {
                    levelOwners.put(pos.immutable(), player.getUUID());
                } else {
                    // 更新为更近的玩家
                    UUID currentOwner = levelOwners.get(pos);
                    Player currentOwnerPlayer = level.getPlayerByUUID(currentOwner);
                    if (currentOwnerPlayer == null || 
                        player.distanceToSqr(Vec3.atCenterOf(pos)) < currentOwnerPlayer.distanceToSqr(Vec3.atCenterOf(pos))) {
                        levelOwners.put(pos.immutable(), player.getUUID());
                    }
                }
                
                // 检查是否到达伤害间隔
                long lastTime = levelDamageTimes.getOrDefault(pos, 0L);
                if (currentTime - lastTime < interval) {
                    continue;
                }
                
                // 执行伤害
                damageHostilesInRange(serverLevel, pos, radius, (float) damageAmount, levelOwners.get(pos));
                levelDamageTimes.put(pos.immutable(), currentTime);
            }
        }
        
        // 清理过期的位置记录
        cleanupExpiredRecords(level, levelDamageTimes, levelOwners, processedPositions);
    }

    private static boolean isValidBlock(BlockState state) {
        if (state.isAir()) return false;
        
        if (state.getBlock() == Blocks.CAMPFIRE && ModConfig.ENABLE_CAMPFIRE.get()) return true;
        if (state.getBlock() == Blocks.SOUL_CAMPFIRE && ModConfig.ENABLE_SOUL_CAMPFIRE.get()) return true;
        if (state.getBlock() == Blocks.TORCH && ModConfig.ENABLE_TORCH.get()) return true;
        if (state.getBlock() == Blocks.SOUL_TORCH && ModConfig.ENABLE_SOUL_TORCH.get()) return true;
        if (state.getBlock() == Blocks.WALL_TORCH && ModConfig.ENABLE_TORCH.get()) return true;
        if (state.getBlock() == Blocks.SOUL_WALL_TORCH && ModConfig.ENABLE_SOUL_TORCH.get()) return true;
        
        return false;
    }

    private static void damageHostilesInRange(ServerLevel level, BlockPos center, int radius, float damage, UUID ownerUUID) {
        AABB area = new AABB(center).inflate(radius);
        
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
            // 跳过非敌对生物
            if (!isHostile(mob)) {
                continue;
            }
            
            // 跳过被命名的生物
            if (ModConfig.IGNORE_NAMED_MOBS.get() && mob.hasCustomName()) {
                continue;
            }
            
            // 跳过已死亡的生物
            if (!mob.isAlive()) {
                continue;
            }
            
            // 创建伤害源
            DamageSource damageSource;
            Player owner = level.getPlayerByUUID(ownerUUID);
            
            if (ModConfig.COUNT_AS_PLAYER_KILL.get() && owner != null) {
                // 使用玩家伤害源，算作玩家击杀
                damageSource = level.damageSources().playerAttack(owner);
            } else {
                // 使用通用伤害源
                damageSource = level.damageSources().generic();
            }
            
            // 标记这个生物的掉落物应该传送到这个位置
            if (ModConfig.TELEPORT_DROPS.get()) {
                entityDropTargets.put(mob.getUUID(), center.immutable());
            }
            
            // 造成伤害
            mob.hurt(damageSource, damage);
        }
    }

    private static boolean isHostile(Mob mob) {
        // 检查是否为敌对生物
        return !mob.getType().getCategory().isFriendly();
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        
        if (level.isClientSide()) {
            return;
        }
        
        // 清理掉落物目标记录
        entityDropTargets.remove(entity.getUUID());
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        
        if (level.isClientSide()) {
            return;
        }
        
        // 检查是否有掉落物传送目标
        BlockPos targetPos = entityDropTargets.get(entity.getUUID());
        if (targetPos == null) {
            return;
        }
        
        // 检查目标位置是否仍然有效
        BlockState state = level.getBlockState(targetPos);
        if (!isValidBlock(state)) {
            return;
        }
        
        // 传送掉落物到目标位置
        for (ItemEntity itemEntity : event.getDrops()) {
            // 设置掉落物位置到篝火/火把位置
            itemEntity.setPos(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
            // 设置速度为0，防止弹跳
            itemEntity.setDeltaMovement(Vec3.ZERO);
        }
        
        // 清理记录
        entityDropTargets.remove(entity.getUUID());
    }

    private static void cleanupExpiredRecords(Level level, Map<BlockPos, Long> damageTimes, 
                                               Map<BlockPos, UUID> owners, Set<BlockPos> activePositions) {
        // 清理不再存在的篝火/火把记录
        damageTimes.keySet().removeIf(pos -> !activePositions.contains(pos) && !isValidBlock(level.getBlockState(pos)));
        owners.keySet().removeIf(pos -> !activePositions.contains(pos) && !isValidBlock(level.getBlockState(pos)));
    }
}