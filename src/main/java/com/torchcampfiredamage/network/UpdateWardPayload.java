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
 */
public record UpdateWardPayload(
        BlockPos pos,
        boolean enabled,
        boolean chunkMode,
        int range,
        double damage,
        int interval,
        boolean glowing,
        boolean slow,
        boolean announce,
        boolean teleportDrops,
        boolean teleportXp,
        boolean ignoreNamed
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
                            buf.readBoolean(),   // enabled
                            buf.readBoolean(),   // chunkMode
                            buf.readVarInt(),    // range
                            buf.readDouble(),    // damage
                            buf.readVarInt(),    // interval
                            buf.readBoolean(),   // glowing
                            buf.readBoolean(),   // slow
                            buf.readBoolean(),   // announce
                            buf.readBoolean(),   // teleportDrops
                            buf.readBoolean(),   // teleportXp
                            buf.readBoolean()    // ignoreNamed
                    );
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, UpdateWardPayload p) {
                    buf.writeBlockPos(p.pos());
                    buf.writeBoolean(p.enabled());
                    buf.writeBoolean(p.chunkMode());
                    buf.writeVarInt(p.range());
                    buf.writeDouble(p.damage());
                    buf.writeVarInt(p.interval());
                    buf.writeBoolean(p.glowing());
                    buf.writeBoolean(p.slow());
                    buf.writeBoolean(p.announce());
                    buf.writeBoolean(p.teleportDrops());
                    buf.writeBoolean(p.teleportXp());
                    buf.writeBoolean(p.ignoreNamed());
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

            ward.applyAll(p.enabled(), p.chunkMode(), p.range(), p.damage(), p.interval(),
                    p.glowing(), p.slow(), p.announce(), p.teleportDrops(), p.teleportXp(),
                    p.ignoreNamed());
        });
    }
}