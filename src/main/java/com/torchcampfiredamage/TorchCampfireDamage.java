package com.torchcampfiredamage;

import com.torchcampfiredamage.block.ModBlocks;
import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.events.DamageEventHandler;
import com.torchcampfiredamage.events.InteractionHandler;
import com.torchcampfiredamage.events.OffhandTorchHandler;
import com.torchcampfiredamage.network.UpdateWardPayload;
import com.torchcampfiredamage.network.UpdateOrbitPayload;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TorchCampfireDamage.MOD_ID)
public class TorchCampfireDamage {
    public static final String MOD_ID = "torchcampfiredamage";
    public static final String MOD_NAME = "Torch Campfire Damage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public TorchCampfireDamage(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[TorchCampfireDamage] Constructing mod...");

        // 配置
        modContainer.registerConfig(Type.COMMON, ModConfig.SPEC);

        // 方块 / 物品 / 方块实体
        ModBlocks.register(modEventBus);

        // 游戏事件
        NeoForge.EVENT_BUS.register(new DamageEventHandler());
        NeoForge.EVENT_BUS.register(new InteractionHandler());
        NeoForge.EVENT_BUS.register(new OffhandTorchHandler());

        // 网络包
        modEventBus.addListener(this::registerPayloads);

        // 创造模式物品栏
        modEventBus.addListener(this::addToCreativeTab);

        LOGGER.info("[TorchCampfireDamage] Mod constructed successfully.");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                UpdateWardPayload.TYPE,
                UpdateWardPayload.CODEC,
                UpdateWardPayload::handle);
        registrar.playToServer(
                UpdateOrbitPayload.TYPE,
                UpdateOrbitPayload.CODEC,
                UpdateOrbitPayload::handle);
        LOGGER.info("[TorchCampfireDamage] Payloads registered.");
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.WARD_TORCH_ITEM.get());
        }
    }
}