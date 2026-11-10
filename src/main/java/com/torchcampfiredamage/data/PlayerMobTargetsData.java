package com.torchcampfiredamage.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.SavedData;

import java.util.*;

public class PlayerMobTargetsData extends SavedData {
    private static final String DATA_NAME = "torchcampfiredamage_player_mob_targets";
    private final Map<UUID, Map<ResourceLocation, Boolean>> players = new HashMap<>();
    private final Map<UUID, Set<ResourceLocation>> discovered = new HashMap<>();

    public static PlayerMobTargetsData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerMobTargetsData::new, PlayerMobTargetsData::load, null), DATA_NAME);
    }

    public static PlayerMobTargetsData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerMobTargetsData data = new PlayerMobTargetsData();
        ListTag playerList = tag.getList("Players", CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag pt = playerList.getCompound(i);
            UUID uuid;
            try { uuid = pt.getUUID("UUID"); } catch (Exception ignored) { continue; }
            Map<ResourceLocation, Boolean> targets = new HashMap<>();
            ListTag entries = pt.getList("Targets", CompoundTag.TAG_COMPOUND);
            for (int j = 0; j < entries.size(); j++) {
                CompoundTag e = entries.getCompound(j);
                ResourceLocation id = ResourceLocation.tryParse(e.getString("Id"));
                if (id != null) targets.put(id, e.getBoolean("Enabled"));
            }
            data.players.put(uuid, targets);
            Set<ResourceLocation> seen = new HashSet<>();
            ListTag seenList = pt.getList("Discovered", CompoundTag.TAG_COMPOUND);
            for (int j = 0; j < seenList.size(); j++) {
                ResourceLocation id = ResourceLocation.tryParse(seenList.getCompound(j).getString("Id"));
                if (id != null) seen.add(id);
            }
            data.discovered.put(uuid, seen);
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        Set<UUID> ids = new HashSet<>(players.keySet());
        ids.addAll(discovered.keySet());
        for (UUID uuid : ids) {
            CompoundTag pt = new CompoundTag();
            pt.putUUID("UUID", uuid);
            ListTag entries = new ListTag();
            for (var e : players.getOrDefault(uuid, Map.of()).entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putString("Id", e.getKey().toString());
                item.putBoolean("Enabled", e.getValue());
                entries.add(item);
            }
            pt.put("Targets", entries);
            ListTag seen = new ListTag();
            for (ResourceLocation id : discovered.getOrDefault(uuid, Set.of())) {
                CompoundTag item = new CompoundTag();
                item.putString("Id", id.toString());
                seen.add(item);
            }
            pt.put("Discovered", seen);
            playerList.add(pt);
        }
        tag.put("Players", playerList);
        return tag;
    }

    public boolean record(ServerPlayer player, Mob mob) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id != null && discover(player.getUUID(), id);
    }
    public boolean discover(UUID player, ResourceLocation id) {
        if (id == null) return false;
        boolean added = discovered.computeIfAbsent(player, k -> new HashSet<>()).add(id);
        if (added) setDirty();
        return added;
    }
    public void delete(UUID player, ResourceLocation id) {
        boolean changed = false;
        Map<ResourceLocation, Boolean> overrides = players.get(player);
        if (overrides != null) changed |= overrides.remove(id) != null;
        Set<ResourceLocation> seen = discovered.get(player);
        if (seen != null) changed |= seen.remove(id);
        if (changed) setDirty();
    }
    public boolean hasOverride(UUID player, EntityType<?> type) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && players.getOrDefault(player, Map.of()).containsKey(id);
    }
    public boolean isEnabled(UUID player, EntityType<?> type) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && players.getOrDefault(player, Map.of()).getOrDefault(id, false);
    }
    public Map<ResourceLocation, Boolean> getTargets(UUID player) {
        return Collections.unmodifiableMap(new TreeMap<>(players.getOrDefault(player, Map.of())));
    }
    public Map<ResourceLocation, Boolean> getEntries(UUID player) {
        Map<ResourceLocation, Boolean> result = new TreeMap<>();
        for (ResourceLocation id : discovered.getOrDefault(player, Set.of())) result.put(id, false);
        result.putAll(players.getOrDefault(player, Map.of()));
        return Collections.unmodifiableMap(result);
    }
    public void setEnabled(UUID player, ResourceLocation id, boolean enabled) {
        if (id == null) return;
        Map<ResourceLocation, Boolean> targets = players.computeIfAbsent(player, k -> new HashMap<>());
        if (!targets.containsKey(id) || targets.get(id) != enabled) {
            targets.put(id, enabled);
            discovered.computeIfAbsent(player, k -> new HashSet<>()).add(id);
            setDirty();
        }
    }
    public void remove(UUID player, ResourceLocation id) {
        Map<ResourceLocation, Boolean> targets = players.get(player);
        if (targets != null && targets.remove(id) != null) setDirty();
    }
}
