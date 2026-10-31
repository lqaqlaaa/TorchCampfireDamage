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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TorchCampfireDamage.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TorchCampfireDamage.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TorchCampfireDamage.MOD_ID);

    /** 守卫火把方块 */
    public static final RegistryObject<Block> WARD_TORCH = BLOCKS.register("ward_torch",
            () -> new WardTorchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .noCollission()
                    .instabreak()
                    .lightLevel(s -> 15)
                    .sound(SoundType.AMETHYST)));

    /** 守卫火把物品（可放置） */
    public static final RegistryObject<Item> WARD_TORCH_ITEM = ITEMS.register("ward_torch",
            () -> new BlockItem(WARD_TORCH.get(), new Item.Properties()));

    /** 守卫护符（只能手持 / 放饰品栏，不能放置） */
    public static final RegistryObject<Item> WARD_CHARM_ITEM = ITEMS.register("ward_charm",
            () -> new WardCharmItem(new Item.Properties().stacksTo(1)));

    /** 方块实体类型 */
    public static final RegistryObject<BlockEntityType<WardSourceBlockEntity>> WARD_SOURCE_BE =
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