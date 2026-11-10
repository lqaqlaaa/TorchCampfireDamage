package com.torchcampfiredamage.client;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client key mappings that are available even when no ward block is targeted. */
@EventBusSubscriber(modid = TorchCampfireDamage.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModKeyMappings {
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.torchcampfiredamage.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.misc"
    );

    private ModKeyMappings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
    }
}
