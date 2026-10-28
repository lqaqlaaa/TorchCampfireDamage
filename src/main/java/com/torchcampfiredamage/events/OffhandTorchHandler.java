package com.torchcampfiredamage.events;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.block.ModBlocks;
import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/**
 * 副手火把效果。
 * <p>
 * 玩家副手持守卫火把时，以其所在区块为中心造成范围伤害。
 * 伤害值 = 主手物品的攻击伤害；间隔默认为每 tick。
 * 默认不播报、不吸掉落物/经验（可在配置里开）。
 */
public class OffhandTorchHandler {
    private static final int CHUNK = SectionPos.SECTION_SIZE;

    /** 玩家UUID -> 上次伤害时间 */
    private static final Map<UUID, Long> lastTime = new HashMap<>();

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        boolean enabled;
        try {
            enabled = ModConfig.OFFHAND_ENABLED.get();
        } catch (Throwable t) {
            return;
        }
        if (!enabled) return;

        int interval;
        int chunkRadius;
        try {
            interval = Math.max(1, ModConfig.OFFHAND_INTERVAL.get());
            chunkRadius = ModConfig.OFFHAND_CHUNK_RADIUS.get();
        } catch (Throwable t) {
            return;
        }

        long now = level.getGameTime();

        for (Player player : level.players()) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) continue;

            // 副手必须是守卫火把
            ItemStack off = sp.getItemInHand(InteractionHand.OFF_HAND);
            if (off.isEmpty() || !off.is(ModBlocks.WARD_TORCH_ITEM.get())) {
                lastTime.remove(sp.getUUID());
                continue;
            }

            long last = lastTime.getOrDefault(sp.getUUID(), Long.MIN_VALUE / 2);
            if (now - last < interval) continue;
            lastTime.put(sp.getUUID(), now);

            // 伤害 = 主手物品攻击力
            float dmg = getAttackDamage(sp);
            if (dmg <= 0) dmg = 1.0f;

            // 1 区块范围（以玩家所在区块为中心）
            int cx = SectionPos.blockToSectionCoord(sp.getBlockX());
            int cz = SectionPos.blockToSectionCoord(sp.getBlockZ());
            double minX = (cx - chunkRadius) * CHUNK;
            double maxX = (cx + chunkRadius + 1) * CHUNK;
            double minZ = (cz - chunkRadius) * CHUNK;
            double maxZ = (cz + chunkRadius + 1) * CHUNK;
            double py = sp.getY();
            AABB area = new AABB(minX, py - 32, minZ, maxX, py + 32, maxZ);

            boolean announce, glowing, slow, tpDrops, tpXp;
            try {
                announce = ModConfig.OFFHAND_ANNOUNCE.get();
                glowing = ModConfig.OFFHAND_GLOWING.get();
                slow = ModConfig.OFFHAND_SLOW.get();
                tpDrops = ModConfig.OFFHAND_TELEPORT_DROPS.get();
                tpXp = ModConfig.OFFHAND_TELEPORT_XP.get();
            } catch (Throwable t) {
                announce = false; glowing = false; slow = false;
                tpDrops = false; tpXp = false;
            }

            Set<UUID> done = new HashSet<>();

            for (Mob mob : level.getEntitiesOfClass(Mob.class, area)) {
                if (!mob.isAlive() || done.contains(mob.getUUID())) continue;
                if (!isHostile(mob)) continue;

                if (glowing) {
                    mob.setGlowingTag(true);
                }
                if (slow) {
                    mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                            100, 254, false, false, true));
                }

                // 传送目标：以玩家位置为准
                if (tpDrops || tpXp) {
                    DamageEventHandler.putDropTarget(mob.getUUID(), sp.blockPosition(), tpDrops, tpXp);
                }

                DamageSource src = level.damageSources().playerAttack(sp);
                boolean ok = mob.hurt(src, dmg);
                if (!ok) {
                    mob.invulnerableTime = 0;
                    mob.setHealth(Math.max(0f, mob.getHealth() - dmg));
                    if (mob.getHealth() <= 0f) mob.die(src);
                }
                done.add(mob.getUUID());

                if (!mob.isAlive() && announce) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6[副手火把] §e" + mob.getName().getString() + "§7 被烧死了"));
                }
            }
        }
    }

    /** 取主手物品的攻击伤害 */
    private static float getAttackDamage(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) return 1.0f;

        if (main.getItem() instanceof SwordItem sword) {
            return sword.getDamage();
        }
        if (main.getItem() instanceof TieredItem tiered) {
            return tiered.getTier().getAttackDamageBonus() + 1.0f;
        }
        // 兜底：用属性系统的攻击伤害
        try {
            var attr = main.getAttributeModifiers()
                    .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            double bonus = 0;
            if (!attr.isEmpty()) {
                bonus = attr.iterator().next().amount();
            }
            return (float) (1.0 + bonus);
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    private static boolean isHostile(Mob mob) {
        if (mob instanceof Enemy) return true;
        try {
            if (!ModConfig.TREAT_PROVOKED_AS_HOSTILE.get()) return false;
        } catch (Throwable t) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null) return false;
        if (target instanceof Player) return true;
        return target instanceof TamableAnimal tameable && tameable.getOwner() != null;
    }
}