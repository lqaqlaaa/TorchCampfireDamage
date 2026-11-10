package com.torchcampfiredamage.events;

import com.torchcampfiredamage.data.PlayerMobTargetsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class PlayerMobTargetHandler {
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getNewDamage() <= 0.0F) return;
        if (player.getServer() == null) return;
        PlayerMobTargetsData.get(player.getServer()).record(player, mob);
    }
}
