package com.torchcampfiredamage;

import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.events.DamageEventHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TorchCampfireDamage.MOD_ID)
public class TorchCampfireDamage {
    public static final String MOD_ID = "torchcampfiredamage";
    public static final String MOD_NAME = "Torch Campfire Damage";

    public TorchCampfireDamage(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置（1.21.1 新 API：通过 ModContainer）
        modContainer.registerConfig(Type.COMMON, ModConfig.SPEC);

        // 注册事件处理器到游戏事件总线
        NeoForge.EVENT_BUS.register(DamageEventHandler.class);
    }
}