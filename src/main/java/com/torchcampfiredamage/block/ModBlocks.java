package com.torchcampfiredamage.block;

import com.torchcampfiredamage.TorchCampfireDamage;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(TorchCampfireDamage.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TorchCampfireDamage.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TorchCampfireDamage.MOD_ID);

    /**
     * 守卫火把 —— 独立方块，可穿透。
     * <p>
     * 关键：
     * <ul>
     *   <li>noCollission() —— 生物/玩家可以穿过（与原版火把一致）</li>
     *   <li>不写 replaceable() —— 否则会被其他方块直接覆盖且不掉落</li>
     *   <li>没有覆写 getCollisionShape() —— 交给 noCollission() 处理</li>
     * </ul>
     */
    public static final DeferredBlock<Block> WARD_TORCH = BLOCKS.register("ward_torch",
            () -> new WardTorchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .instabreak()
                    .lightLevel(s -> 15)
                    .sound(SoundType.AMETHYST)));

    /** 守卫火把方块物品（可以放置） */
    public static final DeferredItem<Item> WARD_TORCH_ITEM = ITEMS.register("ward_torch",
            () -> new BlockItem(WARD_TORCH.get(), new Item.Properties()));

    /** 守卫护符（只能手持，不能放置） */
    public static final DeferredItem<Item> WARD_CHARM_ITEM = ITEMS.register("ward_charm",
            () -> new WardCharmItem(new Item.Properties().stacksTo(1)));

    /** 共用一个 BlockEntity 类型 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WardSourceBlockEntity>> WARD_SOURCE_BE =
            BLOCK_ENTITIES.register("ward_source", () -> BlockEntityType.Builder.of(
                    WardSourceBlockEntity::new,
                    WARD_TORCH.get()
            ).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}