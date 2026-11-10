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

/** 单根放置火把的附魔配置：action 0=设置等级，1=移除。 */
public record WardEnchantmentsPayload(BlockPos pos, int action, String enchantmentId, int level)
        implements CustomPacketPayload {
    public static final Type<WardEnchantmentsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "ward_enchantments"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WardEnchantmentsPayload> CODEC = new StreamCodec<>() {
        @Override public WardEnchantmentsPayload decode(RegistryFriendlyByteBuf buf) {
            return new WardEnchantmentsPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, WardEnchantmentsPayload payload) {
            buf.writeBlockPos(payload.pos());
            buf.writeVarInt(payload.action());
            buf.writeUtf(payload.enchantmentId(), 256);
            buf.writeVarInt(payload.level());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WardEnchantmentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) return;
            BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;
            BlockEntity raw = level.getBlockEntity(pos);
            if (!(raw instanceof WardSourceBlockEntity ward)) return;
            ResourceLocation id = ResourceLocation.tryParse(payload.enchantmentId());
            if (id == null || !level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).containsKey(id)) return;
            if (payload.action() == 0 && payload.level() > 0) {
                ward.setAttackEnchantment(id, payload.level());
            } else if (payload.action() == 1) {
                ward.removeAttackEnchantment(id);
            }
        });
    }
}
