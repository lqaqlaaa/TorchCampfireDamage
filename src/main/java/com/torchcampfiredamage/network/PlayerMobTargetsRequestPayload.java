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

public record PlayerMobTargetsRequestPayload(int action, String entityId, boolean enabled) implements CustomPacketPayload {
    public static final Type<PlayerMobTargetsRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "player_mob_targets_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMobTargetsRequestPayload> CODEC = new StreamCodec<>() {
        public PlayerMobTargetsRequestPayload decode(RegistryFriendlyByteBuf b) {
            return new PlayerMobTargetsRequestPayload(b.readVarInt(), b.readUtf(256), b.readBoolean());
        }
        public void encode(RegistryFriendlyByteBuf b, PlayerMobTargetsRequestPayload p) {
            b.writeVarInt(p.action()); b.writeUtf(p.entityId(),256); b.writeBoolean(p.enabled());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(PlayerMobTargetsRequestPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player) || player.getServer()==null) return;
            PlayerMobTargetsData data=PlayerMobTargetsData.get(player.getServer());
            ResourceLocation id = p.action()==0 ? null : ResourceLocation.tryParse(p.entityId());
            if (p.action()!=0 && id==null) return;
            switch(p.action()) {
                case 0 -> {}
                case 1 -> data.setEnabled(player.getUUID(),id,p.enabled());
                case 2 -> data.remove(player.getUUID(),id);
                case 3 -> data.delete(player.getUUID(),id);
                case 4 -> { data.discover(player.getUUID(),id,p.enabled()); return; }
                default -> { return; }
            }
            var entries=data.getEntries(player.getUUID()).entrySet().stream()
                    .map(e -> new PlayerMobTargetsSyncPayload.Entry(e.getKey().toString(),e.getValue(),
                            data.getTargets(player.getUUID()).containsKey(e.getKey())))
                    .toList();
            PacketDistributor.sendToPlayer(player,new PlayerMobTargetsSyncPayload(entries));
        });
    }
}
