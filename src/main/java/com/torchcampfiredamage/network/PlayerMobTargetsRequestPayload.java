package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.data.PlayerMobTargetsData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerMobTargetsRequestPayload(int action, String entityId, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<PlayerMobTargetsRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "player_mob_targets_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMobTargetsRequestPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public PlayerMobTargetsRequestPayload decode(RegistryFriendlyByteBuf buf) {
                    return new PlayerMobTargetsRequestPayload(buf.readVarInt(), buf.readUtf(256), buf.readBoolean());
                }
                @Override
                public void encode(RegistryFriendlyByteBuf buf, PlayerMobTargetsRequestPayload payload) {
                    buf.writeVarInt(payload.action());
                    buf.writeUtf(payload.entityId(), 256);
                    buf.writeBoolean(payload.enabled());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PlayerMobTargetsRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.getServer() == null) return;
            PlayerMobTargetsData data = PlayerMobTargetsData.get(player.getServer());
            if (payload.action() == 1 || payload.action() == 2) {
                ResourceLocation id = ResourceLocation.tryParse(payload.entityId());
                if (id == null) return;
                if (payload.action() == 1) data.setEnabled(player.getUUID(), id, payload.enabled());
                else data.remove(player.getUUID(), id);
            } else if (payload.action() != 0) {
                return;
            }
            var entries = data.getTargets(player.getUUID()).entrySet().stream()
                    .map(entry -> new PlayerMobTargetsSyncPayload.Entry(entry.getKey().toString(), entry.getValue()))
                    .toList();
            PacketDistributor.sendToPlayer(player, new PlayerMobTargetsSyncPayload(entries));
        });
    }
}
