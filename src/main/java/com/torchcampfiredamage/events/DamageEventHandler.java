package com.torchcampfiredamage.events;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.block.WardSourceBlockEntity;
import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
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
 * 守卫火把伤害处理器。
 *
 * 性能设计：
 *  - 普通 HashMap（不用 WeakHashMap，避免 get 时 expunge 全表）
 *  - 全局计数器 TOTAL_REGISTERED，无火把时零成本返回
 *  - 清理每 20 tick 一次
 *
 * 传送设计：
 *  - LivingDropsEvent 与 LivingExperienceDropEvent 顺序不固定
 *  - CONSUMED 计数，两个事件都处理完才移除标记
 */
public class DamageEventHandler {
    private static final int SCAN_STEP = 5;
    private static final int CHUNK_SIZE = SectionPos.SECTION_SIZE;
    private static final int CLEAN_PERIOD = 20;

    private static final Map<Level, Set<BlockPos>> REGISTERED = new HashMap<>();
    private static final Map<Level, Map<BlockPos, Long>> lastDamageTimes = new HashMap<>();
    private static final Map<Level, Map<BlockPos, UUID>> positionOwners = new HashMap<>();
    private static int TOTAL_REGISTERED = 0;

    private static final Map<UUID, DropTarget> entityDropTargets = new HashMap<>();
    private static final Map<UUID, Integer> CONSUMED = new HashMap<>();
    private static final Map<UUID, Long> pendingCleanup = new HashMap<>();

    private static final Map<Mob, Long> GLOW_EXPIRY = new HashMap<>();
    private static boolean DEBUG_PRINTED = false;

    public record DropTarget(BlockPos pos, boolean drops, boolean xp, int lootingLevel) {}

    /* ================= 登记 ================= */

    public static void register(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Set<BlockPos> set = REGISTERED.get(level);
        if (set == null) {
            set = new LinkedHashSet<>();
            REGISTERED.put(level, set);
        }
        if (set.add(pos)) TOTAL_REGISTERED++;
    }

    public static void unregister(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        Set<BlockPos> set = REGISTERED.get(level);
        if (set != null && set.remove(pos)) {
            TOTAL_REGISTERED--;
            if (TOTAL_REGISTERED < 0) TOTAL_REGISTERED = 0;
        }
        Map<BlockPos, Long> t = lastDamageTimes.get(level);
        if (t != null) t.remove(pos);
        Map<BlockPos, UUID> o = positionOwners.get(level);
        if (o != null) o.remove(pos);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        TorchCampfireDamage.LOGGER.info("[TorchCampfireDamage] Server started. Ward torches ready.");
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
        if (!(level instanceof ServerLevel serverLevel)) return;
        try {
            handle(serverLevel);
        } catch (Throwable t) {
            TorchCampfireDamage.LOGGER.error("[TorchCampfireDamage] tick error", t);
        }
    }

    private static void handle(ServerLevel level) {
        if (TOTAL_REGISTERED == 0 && pendingCleanup.isEmpty() && GLOW_EXPIRY.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();

        if (gameTime % CLEAN_PERIOD == 0) {
            if (!GLOW_EXPIRY.isEmpty()) tickGlowExpiry(level);
            if (!pendingCleanup.isEmpty()) cleanPending(level, gameTime);
        }

        if (TOTAL_REGISTERED == 0) return;

        Set<BlockPos> torches = REGISTERED.get(level);
        if (torches == null || torches.isEmpty()) return;

        if (!DEBUG_PRINTED) {
            DEBUG_PRINTED = true;
            TorchCampfireDamage.LOGGER.info("[TorchCampfireDamage] indexed tick active, torches=" + torches.size());
        }

        Map<BlockPos, Long> damageTimes = lastDamageTimes.computeIfAbsent(level, k -> new HashMap<>());
        Map<BlockPos, UUID> owners = positionOwners.computeIfAbsent(level, k -> new HashMap<>());

        Set<UUID> damagedThisTick = new HashSet<>();
        // LinkedHashSet 保留服务端登记顺序；较早登记的火把在重叠区域拥有优先权。
        List<BlockPos> snapshot = new ArrayList<>(torches);
        List<? extends Player> players = level.players();

        for (BlockPos ip : snapshot) {
            BlockEntity be = level.getBlockEntity(ip);
            if (!(be instanceof WardSourceBlockEntity ward)) {
                unregister(level, ip);
                continue;
            }
            if (!ward.isEnabled()) continue;
            // 将不同位置的火把分散到不同 tick 扫描，避免大量火把在同一 tick 集中查询大范围实体。
            if (Math.floorMod(gameTime + ip.hashCode(), SCAN_STEP) != 0) continue;

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

            damageHostiles(level, ip, ward, owners.get(ip), damagedThisTick, gameTime, snapshot);
            damageTimes.put(ip, gameTime);
        }
    }


    private static boolean claimedByEarlierTorch(ServerLevel level, BlockPos current, Mob mob,
                                                   List<BlockPos> orderedTorches, boolean ignoreNamed) {
        for (BlockPos earlierPos : orderedTorches) {
            if (earlierPos.equals(current)) return false;
            BlockEntity earlierBe = level.getBlockEntity(earlierPos);
            if (!(earlierBe instanceof WardSourceBlockEntity earlier) || !earlier.isEnabled()) continue;
            if (!damageArea(earlierPos, earlier).contains(mob.position())) continue;
            if (ignoreNamed && earlier.isIgnoreNamed() && mob.hasCustomName()) continue;
            if (earlier.hasMobTargetOverride(mob.getType())) {
                if (!earlier.isMobTargetAllowed(mob.getType())) continue;
            } else if (!isHostileToPlayers(mob)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /* ================= 伤害 ================= */

    private static void damageHostiles(ServerLevel level, BlockPos center, WardSourceBlockEntity ward,
                                       UUID ownerUUID, Set<UUID> damagedThisTick, long gameTime, List<BlockPos> orderedTorches) {
        AABB area = damageArea(center, ward);
        Player owner = ownerUUID == null ? null : level.getPlayerByUUID(ownerUUID);

        float damage = (float) ward.getDamage();
        boolean glowing = ward.isGlowing();
        boolean slow = ward.isSlow();
        boolean announce = ward.isAnnounce();
        boolean tpDrops = ward.isTeleportDrops();
        boolean tpXp = ward.isTeleportXp();

        boolean countAsKill = safeGet(ModConfig.COUNT_AS_PLAYER_KILL::get, true);
        // 命名生物白名单：优先使用本火把自己的设置（右键界面可单独调整）
        boolean ignoreNamed = ward.isIgnoreNamed();

        // Minecraft 的实体查询本身会利用实体区块分区；把大 AABB 切成许多小 AABB
        // 会重复访问相邻区域、创建大量列表并合并结果。一次查询即可避免这些额外开销。
        java.util.List<Mob> candidates = level.getEntitiesOfClass(Mob.class, area);

        for (Mob mob : candidates) {
            if (!mob.isAlive()) continue;
            // 生物发现记录属于服务端范围扫描，不依赖玩家打开名单界面；
            // 必须在命名保护、目标过滤和造成伤害之前记录，避免低血量生物被瞬间击杀后漏记。
            ResourceLocation discoveredId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if (discoveredId != null) ward.discoverMobTarget(discoveredId, isHostileToPlayers(mob));
            if (damagedThisTick.contains(mob.getUUID())) continue;
            if (ignoreNamed && mob.hasCustomName()) continue;
            // 重叠区域归属：按服务端登记顺序检查更早的火把。
            // 更早火把禁用、卸载、名单排除此生物或不在范围内时，不占用归属，后续火把可接管。
            if (claimedByEarlierTorch(level, center, mob, orderedTorches, ignoreNamed)) continue;
            // 只有明确添加到本火把名单中的类型才覆盖默认行为。
            // 未配置的生物继续使用旧版敌对/仇恨判定，避免添加一个目标后误伤默认规则。
            if (ward.hasMobTargetOverride(mob.getType())) {
                if (!ward.isMobTargetAllowed(mob.getType())) continue;
            } else if (!isHostileToPlayers(mob)) {
                continue;
            }

            if (glowing) {
                mob.setGlowingTag(true);
                GLOW_EXPIRY.put(mob, gameTime + safeGet(ModConfig.GLOW_DURATION::get, 100));
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
                UUID id = mob.getUUID();
                int lootingLevel = ward.getAttackEnchantments().entrySet().stream()
                        .filter(entry -> entry.getKey().toString().equals("minecraft:looting"))
                        .mapToInt(Map.Entry::getValue).max().orElse(0);
                entityDropTargets.put(id, new DropTarget(center, tpDrops, tpXp, Math.max(0, lootingLevel)));
                CONSUMED.remove(id);
                pendingCleanup.put(id, gameTime);
            }

            ItemStack enchantmentSource = buildEnchantmentSource(level, ward, owner);
            float finalDamage = enchantmentSource.isEmpty()
                    ? damage : EnchantmentHelper.modifyDamage(level, enchantmentSource, mob, src, damage);
            boolean hurtOk = mob.hurt(src, finalDamage);
            if (hurtOk && !enchantmentSource.isEmpty()) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(level, mob, src, enchantmentSource);
            }
            if (!hurtOk) {
                mob.invulnerableTime = 0;
                float nh = mob.getHealth() - finalDamage;
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
            } else {
                // 伤害没有在本次调用中击杀目标时，立即撤销掉落归属。
                // 否则玩家或其他伤害来源在清理窗口内击杀它，会错误地把掉落传送到火把。
                entityDropTargets.remove(mob.getUUID());
                CONSUMED.remove(mob.getUUID());
                pendingCleanup.remove(mob.getUUID());
            }
        }
    }


    private static ItemStack buildEnchantmentSource(ServerLevel level, WardSourceBlockEntity ward, Player owner) {
        if (ward.getAttackEnchantments().isEmpty()) return ItemStack.EMPTY;
        // Use the owner's actual weapon item as the enchantment carrier. Some modded enchantments
        // validate supported item tags during effect evaluation; a synthetic diamond sword
        // silently disables enchantments that are valid on the player's real weapon.
        ItemStack source = owner != null && !owner.getMainHandItem().isEmpty()
                ? owner.getMainHandItem().copyWithCount(1)
                : new ItemStack(Items.DIAMOND_SWORD);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (var entry : ward.getAttackEnchantments().entrySet()) {
            var holder = registry.getHolder(entry.getKey());
            if (holder.isPresent() && entry.getValue() > 0) {
                enchantments.set(holder.get(), entry.getValue());
            }
        }
        ItemEnchantments built = enchantments.toImmutable();
        if (built.isEmpty()) return ItemStack.EMPTY;
        source.set(DataComponents.ENCHANTMENTS, built);
        return source;
    }

    /* ================= 清理 ================= */

    private static void tickGlowExpiry(Level level) {
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

    private static void cleanPending(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, Long>> it = pendingCleanup.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            if (now - e.getValue() > 200) {
                UUID id = e.getKey();
                entityDropTargets.remove(id);
                CONSUMED.remove(id);
                it.remove();
            }
        }
    }

    /** 供副手火把等外部逻辑登记掉落物/经验传送目标 */
    public static void putDropTarget(UUID entityId, net.minecraft.core.BlockPos pos,
                                     boolean drops, boolean xp) {
        if (drops || xp) {
            entityDropTargets.put(entityId, new DropTarget(pos, drops, xp, 0));
            CONSUMED.remove(entityId);
            pendingCleanup.put(entityId, 0L);
        }
    }

    private static void markConsumed(UUID id) {
        int n = CONSUMED.getOrDefault(id, 0) + 1;
        if (n >= 2) {
            CONSUMED.remove(id);
            entityDropTargets.remove(id);
            pendingCleanup.remove(id);
        } else {
            CONSUMED.put(id, n);
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

        UUID id = entity.getUUID();
        DropTarget target = entityDropTargets.get(id);

        if (target != null && target.drops()) {
            BlockPos tp = target.pos();
            for (ItemEntity item : event.getDrops()) {
                item.setPos(tp.getX() + 0.5, tp.getY() + 1.0, tp.getZ() + 0.5);
                item.setDeltaMovement(Vec3.ZERO);
            }
        }
        if (target != null) {
            markConsumed(id);
        }
    }

    /* ================= 经验传送 ================= */

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;

        UUID id = entity.getUUID();
        DropTarget target = entityDropTargets.get(id);

        if (target != null && target.xp() && safeGet(ModConfig.TELEPORT_XP::get, true)) {
            int xp = event.getDroppedExperience();
            if (xp > 0) {
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
        if (target != null) {
            markConsumed(id);
        }
    }
}