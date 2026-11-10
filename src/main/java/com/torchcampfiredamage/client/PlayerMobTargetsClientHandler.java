package com.torchcampfiredamage.client;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.network.PlayerMobTargetsSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = TorchCampfireDamage.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PlayerMobTargetsClientHandler {
    private PlayerMobTargetsClientHandler() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                PlayerMobTargetsSyncPayload.TYPE,
                PlayerMobTargetsSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        MobTargetsScreen.receive(payload.entries())));
    }
}
