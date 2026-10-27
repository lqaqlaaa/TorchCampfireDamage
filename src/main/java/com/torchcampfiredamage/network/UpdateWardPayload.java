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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：保存守卫方块参数。
 * 手动实现 codec（字段较多，超出 StreamCodec.composite 的 8 组上限）。
 */
public record UpdateWardPayload(
        BlockPos pos,
        int chunkRadius,
        double damage,
        int interval,
        boolean announce,
        boolean glowing,
        boolean slow,
        int slowAmplifier,
        boolean enabled
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "update_ward");

    public static final Type<UpdateWardPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWardPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public UpdateWardPayload decode(RegistryFriendlyByteBuf buf) {
                    return new UpdateWardPayload(
                            buf.readBlockPos(),
                            buf.readVarInt(),
                            buf.readDouble(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarInt(),
                            buf.readBoolean()
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, UpdateWardPayload p) {
                    buf.writeBlockPos(p.pos());
                    buf.writeVarInt(p.chunkRadius());
                    buf.writeDouble(p.damage());
                    buf.writeVarInt(p.interval());
                    buf.writeBoolean(p.announce());
                    buf.writeBoolean(p.glowing());
                    buf.writeBoolean(p.slow());
                    buf.writeVarInt(p.slowAmplifier());
                    buf.writeBoolean(p.enabled());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateWardPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            BlockPos pos = p.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WardSourceBlockEntity ward)) return;

            ward.applyAll(p.chunkRadius(), p.damage(), p.interval(),
                    p.announce(), p.glowing(), p.slow(), p.slowAmplifier(), p.enabled());
        });
    }
}