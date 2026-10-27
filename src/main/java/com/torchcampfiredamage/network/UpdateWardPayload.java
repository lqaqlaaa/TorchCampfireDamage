package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：保存某个守卫方块的全部参数（直接写入它的 BlockEntity）。
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
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UpdateWardPayload::pos,
                    ByteBufCodecs.VAR_INT, UpdateWardPayload::chunkRadius,
                    ByteBufCodecs.DOUBLE, UpdateWardPayload::damage,
                    ByteBufCodecs.VAR_INT, UpdateWardPayload::interval,
                    ByteBufCodecs.BOOL, UpdateWardPayload::announce,
                    ByteBufCodecs.BOOL, UpdateWardPayload::glowing,
                    ByteBufCodecs.BOOL, UpdateWardPayload::slow,
                    ByteBufCodecs.VAR_INT, UpdateWardPayload::slowAmplifier,
                    ByteBufCodecs.BOOL, UpdateWardPayload::enabled,
                    UpdateWardPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateWardPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            BlockPos pos = p.pos();
            // 防远程修改
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WardSourceBlockEntity ward)) {
                return;
            }

            ward.applyAll(p.chunkRadius(), p.damage(), p.interval(),
                    p.announce(), p.glowing(), p.slow(), p.slowAmplifier(), p.enabled());
        });
    }
}