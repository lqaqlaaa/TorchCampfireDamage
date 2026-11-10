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

/** 单根放置火把的伤害类型配置：action 2=启用/禁用类型。 */
public record WardDamageTypesPayload(BlockPos pos, int action, String damageTypeId, int level)
        implements CustomPacketPayload {
    public static final Type<WardDamageTypesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "ward_damage_types"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WardDamageTypesPayload> CODEC = new StreamCodec<>() {
        @Override public WardDamageTypesPayload decode(RegistryFriendlyByteBuf buf) {
            return new WardDamageTypesPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, WardDamageTypesPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.action());
            buf.writeUtf(payload.damageTypeId(), 256);
            buf.writeVarInt(payload.level());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WardDamageTypesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) return;
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            BlockEntity raw = level.getBlockEntity(pos);
            if (!(raw instanceof WardSourceBlockEntity ward)) return;
            if (payload.damageTypeId() == null || payload.damageTypeId().length() > 32) return;
            if (payload.action() == 3 && payload.damageTypeId().equals("one_hit")) {
                ward.setForceOneHealth(payload.level() > 0);
                return;
            }
            if (payload.action() != 2) return;
            ward.setSpecialDamageType(payload.damageTypeId(), payload.level() > 0);
        });
    }
}
