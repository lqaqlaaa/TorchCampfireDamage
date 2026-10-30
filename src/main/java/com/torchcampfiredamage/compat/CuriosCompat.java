package com.torchcampfiredamage.compat;

import com.torchcampfiredamage.block.ModBlocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosCapability;

/**
 * 把守卫护符注册成 Curios 饰品。
 * <p>
 * 做法：挂载 Curios 的 {@code CuriosCapability.ITEM} 能力，
 * 这样 Curios 的饰品栏就会认可这件物品。
 * <p>
 * 槽位归属由数据包标签决定：
 * {@code data/curios/tags/item/charm.json}（外加项链/护身符通用槽位）。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", bus = EventBusSubscriber.Bus.MOD)
public class CuriosCompat {

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                CuriosCapability.ITEM,
                (stack, ctx) -> new WardCharmCurio(stack),
                ModBlocks.WARD_CHARM_ITEM.get()
        );
    }
}