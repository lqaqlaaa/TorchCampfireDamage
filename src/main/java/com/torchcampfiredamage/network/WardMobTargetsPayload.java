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

/** 修改某一根守卫火把自己的攻击生物名单。 */
public record WardMobTargetsPayload(BlockPos pos, int action, String entityId, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<WardMobTargetsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "ward_mob_targets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WardMobTargetsPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public WardMobTargetsPayload decode(RegistryFriendlyByteBuf buf) {
                    return new WardMobTargetsPayload(buf.readBlockPos(), buf.readVarInt(),
                            buf.readUtf(256), buf.readBoolean());
                }
                @Override
                public void encode(RegistryFriendlyByteBuf buf, WardMobTargetsPayload p) {
                    buf.writeBlockPos(p.pos());
                    buf.writeVarInt(p.action());
                    buf.writeUtf(p.entityId(), 256);
                    buf.writeBoolean(p.enabled());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WardMobTargetsPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) return;
            if (player.distanceToSqr(p.pos().getX() + 0.5, p.pos().getY() + 0.5,
                    p.pos().getZ() + 0.5) > 64.0) return;
            BlockEntity blockEntity = level.getBlockEntity(p.pos());
            if (!(blockEntity instanceof WardSourceBlockEntity ward)) return;
            ResourceLocation id = ResourceLocation.tryParse(p.entityId());
            if (id == null) return;
            if (p.action() == 0) ward.setMobTarget(id, p.enabled());
            else if (p.action() == 1) ward.removeMobTarget(id);
        });
    }
}
