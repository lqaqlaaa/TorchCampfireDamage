package com.torchcampfiredamage;

import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.events.DamageEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TorchCampfireDamage.MOD_ID)
public class TorchCampfireDamage {
    public static final String MOD_ID = "torchcampfiredamage";
    public static final String MOD_NAME = "Torch Campfire Damage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public TorchCampfireDamage(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[TorchCampfireDamage] Constructing mod...");

        // 注册配置
        modContainer.registerConfig(Type.COMMON, ModConfig.SPEC);
        LOGGER.info("[TorchCampfireDamage] Config registered.");

        // 用实例注册，确保 @SubscribeEvent 被扫描
        NeoForge.EVENT_BUS.register(new DamageEventHandler());
        LOGGER.info("[TorchCampfireDamage] Event handler registered on NeoForge.EVENT_BUS.");

        LOGGER.info("[TorchCampfireDamage] Mod constructed successfully.");
    }
}