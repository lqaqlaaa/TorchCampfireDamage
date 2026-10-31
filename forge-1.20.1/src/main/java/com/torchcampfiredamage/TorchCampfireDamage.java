package com.torchcampfiredamage;

import com.torchcampfiredamage.block.ModBlocks;
import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.events.DamageEventHandler;
import com.torchcampfiredamage.events.InteractionHandler;
import com.torchcampfiredamage.events.OffhandTorchHandler;
import com.torchcampfiredamage.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 守卫火把 —— Forge 1.20.1 版本。
 */
@Mod(TorchCampfireDamage.MOD_ID)
public class TorchCampfireDamage {
    public static final String MOD_ID = "torchcampfiredamage";
    public static final String MOD_NAME = "Torch Campfire Damage";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public TorchCampfireDamage() {
        LOGGER.info("[TorchCampfireDamage] Constructing mod (Forge 1.20.1)...");

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        // 配置
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC);

        // 方块 / 物品 / 方块实体
        ModBlocks.register(modBus);

        // 网络
        ModNetwork.register();

        // 游戏事件
        forgeBus.register(new DamageEventHandler());
        forgeBus.register(new InteractionHandler());
        forgeBus.register(new OffhandTorchHandler());

        // 创造模式物品栏
        modBus.addListener(this::addToCreativeTab);

        LOGGER.info("[TorchCampfireDamage] Mod constructed successfully.");
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.WARD_TORCH_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModBlocks.WARD_CHARM_ITEM);
        }
    }
}