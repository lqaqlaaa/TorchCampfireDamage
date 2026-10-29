package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：保存火把的「环上旋转物品」设置。
 */
public record UpdateOrbitPayload(
        BlockPos pos,
        ItemStack[] items,
        boolean visible,
        float radius
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "update_orbit");

    public static final Type<UpdateOrbitPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateOrbitPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public UpdateOrbitPayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    ItemStack[] arr = new ItemStack[4];
                    for (int i = 0; i < 4; i++) {
                        arr[i] = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    }
                    boolean vis = buf.readBoolean();
                    float r = buf.readFloat();
                    return new UpdateOrbitPayload(pos, arr, vis, r);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, UpdateOrbitPayload p) {
                    buf.writeBlockPos(p.pos());
                    for (int i = 0; i < 4; i++) {
                        ItemStack st = (p.items() != null && i < p.items().length && p.items()[i] != null)
                                ? p.items()[i] : ItemStack.EMPTY;
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, st);
                    }
                    buf.writeBoolean(p.visible());
                    buf.writeFloat(p.radius());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateOrbitPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            BlockPos pos = p.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WardSourceBlockEntity ward)) return;

            for (int i = 0; i < 4; i++) {
                ItemStack st = (p.items() != null && i < p.items().length) ? p.items()[i] : ItemStack.EMPTY;
                ward.setOrbitItem(i, st);
            }
            ward.setOrbitVisible(p.visible());
            ward.setOrbitRadius(p.radius());
        });
    }
}