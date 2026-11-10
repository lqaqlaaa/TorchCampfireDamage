package com.torchcampfiredamage.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PlayerMobTargetsData extends SavedData {
    private static final String DATA_NAME = "torchcampfiredamage_player_mob_targets";
    private final Map<UUID, Map<ResourceLocation, Boolean>> players = new HashMap<>();

    public static PlayerMobTargetsData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerMobTargetsData::new, PlayerMobTargetsData::load, null),
                DATA_NAME);
    }

    public static PlayerMobTargetsData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerMobTargetsData data = new PlayerMobTargetsData();
        ListTag playerList = tag.getList("Players", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            UUID uuid;
            try { uuid = playerTag.getUUID("UUID"); } catch (Exception ignored) { continue; }
            Map<ResourceLocation, Boolean> targets = new HashMap<>();
            ListTag entries = playerTag.getList("Targets", CompoundTag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag entry = entries.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("Id"));
                if (id != null) targets.put(id, entry.getBoolean("Enabled"));
            }
            data.players.put(uuid, targets);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        for (var playerEntry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("UUID", playerEntry.getKey());
            ListTag entries = new ListTag();
            for (var target : playerEntry.getValue().entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("Id", target.getKey().toString());
                entry.putBoolean("Enabled", target.getValue());
                entries.add(entry);
            }
            playerTag.put("Targets", entries);
            playerList.add(playerTag);
        }
        tag.put("Players", playerList);
        return tag;
    }

    public boolean record(ServerPlayer player, Mob mob) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (id == null) return false;
        Map<ResourceLocation, Boolean> targets = players.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        if (targets.containsKey(id)) return false;
        targets.put(id, true);
        setDirty();
        return true;
    }

    public boolean hasOverride(UUID player, EntityType<?> type) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && players.getOrDefault(player, Map.of()).containsKey(id);
    }

    public boolean isEnabled(UUID player, EntityType<?> type) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null) return false;
        return players.getOrDefault(player, Map.of()).getOrDefault(id, false);
    }

    public Map<ResourceLocation, Boolean> getTargets(UUID player) {
        return Collections.unmodifiableMap(new TreeMap<>(players.getOrDefault(player, Map.of())));
    }

    public void setEnabled(UUID player, ResourceLocation id, boolean enabled) {
        Map<ResourceLocation, Boolean> targets = players.computeIfAbsent(player, k -> new HashMap<>());
        if (!targets.containsKey(id) || targets.get(id) != enabled) {
            targets.put(id, enabled);
            setDirty();
        }
    }

    public void remove(UUID player, ResourceLocation id) {
        Map<ResourceLocation, Boolean> targets = players.get(player);
        if (targets != null && targets.remove(id) != null) setDirty();
    }
}
