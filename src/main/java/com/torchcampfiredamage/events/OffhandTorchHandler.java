package com.torchcampfiredamage.events;

import com.torchcampfiredamage.block.ModBlocks;
import com.torchcampfiredamage.config.ModConfig;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/**
 * 副手火把效果。
 * <p>
 * 玩家副手持守卫火把时，以其所在区块为中心造成范围伤害。
 * 伤害值取玩家当前的 ATTACK_DAMAGE 属性（含武器/附魔/修饰符）。
 * 默认每 tick 一次、不播报、不吸掉落物/经验。
 */
public class OffhandTorchHandler {
    private static final int CHUNK = SectionPos.SECTION_SIZE;

    private static final Map<UUID, Long> lastTime = new HashMap<>();

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        boolean enabled;
        int interval, chunkRadius;
        try {
            enabled = ModConfig.OFFHAND_ENABLED.get();
            if (!enabled) return;
            interval = Math.max(1, ModConfig.OFFHAND_INTERVAL.get());
            chunkRadius = ModConfig.OFFHAND_CHUNK_RADIUS.get();
        } catch (Throwable t) {
            return;
        }

        long now = level.getGameTime();

        for (Player p : level.players()) {
            if (!(p instanceof ServerPlayer sp)) continue;

            // 副手必须是守卫火把
            ItemStack off = sp.getItemInHand(InteractionHand.OFF_HAND);
            if (off.isEmpty() || !off.is(ModBlocks.WARD_TORCH_ITEM.get())) {
                lastTime.remove(sp.getUUID());
                continue;
            }

            long last = lastTime.getOrDefault(sp.getUUID(), Long.MIN_VALUE / 2);
            if (now - last < interval) continue;
            lastTime.put(sp.getUUID(), now);

            float dmg = getAttackDamage(sp);

            int cx = SectionPos.blockToSectionCoord(sp.getBlockX());
            int cz = SectionPos.blockToSectionCoord(sp.getBlockZ());
            AABB area = new AABB(
                    (double) (cx - chunkRadius) * CHUNK,
                    sp.getY() - 32,
                    (double) (cz - chunkRadius) * CHUNK,
                    (double) (cx + chunkRadius + 1) * CHUNK,
                    sp.getY() + 32,
                    (double) (cz + chunkRadius + 1) * CHUNK);

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

                if (glowing) mob.setGlowingTag(true);
                if (slow) {
                    mob.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 100, 254, false, false, true));
                }

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
                    sp.sendSystemMessage(Component.literal(
                            "§6[副手火把] §e" + mob.getName().getString() + "§7 被烧死了"));
                }
            }
        }
    }

    /** 取玩家当前攻击力（含武器/附魔/修饰符） */
    private static float getAttackDamage(ServerPlayer player) {
        try {
            var attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attr != null) {
                float v = (float) attr.getValue();
                return v > 0 ? v : 1.0f;
            }
        } catch (Throwable ignored) {
        }
        return 1.0f;
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