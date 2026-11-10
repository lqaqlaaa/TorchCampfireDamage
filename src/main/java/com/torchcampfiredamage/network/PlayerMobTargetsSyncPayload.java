package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public record PlayerMobTargetsSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(String entityId, boolean enabled, boolean configured) {
        public Entry(String entityId, boolean enabled) { this(entityId, enabled, true); }
    }
    public static final Type<PlayerMobTargetsSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "player_mob_targets_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMobTargetsSyncPayload> CODEC = new StreamCodec<>() {
        public PlayerMobTargetsSyncPayload decode(RegistryFriendlyByteBuf buf) {
            int size = Math.min(buf.readVarInt(), 4096);
            List<Entry> entries = new ArrayList<>(size);
            for (int i=0;i<size;i++) entries.add(new Entry(buf.readUtf(256), buf.readBoolean(), buf.readBoolean()));
            return new PlayerMobTargetsSyncPayload(entries);
        }
        public void encode(RegistryFriendlyByteBuf buf, PlayerMobTargetsSyncPayload p) {
            List<Entry> entries = p.entries() == null ? List.of() : p.entries();
            int size = Math.min(entries.size(), 4096);
            buf.writeVarInt(size);
            for (int i=0;i<size;i++) {
                Entry e=entries.get(i); buf.writeUtf(e.entityId(),256); buf.writeBoolean(e.enabled()); buf.writeBoolean(e.configured());
            }
        }
    };
    public PlayerMobTargetsSyncPayload { entries = entries == null ? List.of() : List.copyOf(entries); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
