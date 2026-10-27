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
import net.minecraft.world.level.material.PushReaction;
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

    /** 守卫火把（落地火把，原版 TorchBlock 特性） */
    public static final DeferredBlock<Block> WARD_TORCH = BLOCKS.register("ward_torch",
            () -> new WardTorchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .noCollission()
                    .instabreak()
                    .lightLevel(s -> 14)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY)));

    /** 墙上守卫火把（原版 WallTorchBlock 特性） */
    public static final DeferredBlock<Block> WARD_WALL_TORCH = BLOCKS.register("ward_wall_torch",
            () -> new WardWallTorchBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .noCollission()
                    .instabreak()
                    .lightLevel(s -> 14)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY)
                    ));

    /** 守卫篝火（原版 CampfireBlock 特性：烹饪/点火/水灭/灼烧） */
    public static final DeferredBlock<Block> WARD_CAMPFIRE = BLOCKS.register("ward_campfire",
            () -> new WardCampfireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0f)
                    .lightLevel(s -> 15)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<Item> WARD_TORCH_ITEM = ITEMS.register("ward_torch",
            () -> new BlockItem(WARD_TORCH.get(), new Item.Properties()));

    public static final DeferredItem<Item> WARD_CAMPFIRE_ITEM = ITEMS.register("ward_campfire",
            () -> new BlockItem(WARD_CAMPFIRE.get(), new Item.Properties()));

    /** 三个方块共用一个 BlockEntity 类型 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WardSourceBlockEntity>> WARD_SOURCE_BE =
            BLOCK_ENTITIES.register("ward_source", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new WardSourceBlockEntity(WARD_SOURCE_BE.get(), pos, state),
                    WARD_TORCH.get(), WARD_WALL_TORCH.get(), WARD_CAMPFIRE.get()
            ).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}