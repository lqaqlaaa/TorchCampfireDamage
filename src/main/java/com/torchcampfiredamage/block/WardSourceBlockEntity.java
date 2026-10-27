package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 守卫火源 BlockEntity —— 参数存在方块自身（跟着方块走，不受坐标变动影响）。
 * <p>
 * 同时承载篝火烹饪槽位（4 个），让守卫篝火具备原版烹饪能力。
 * 火把不使用这些槽位，但保留数据结构无副作用。
 */
public class WardSourceBlockEntity extends net.minecraft.world.level.block.entity.CampfireBlockEntity {

    /* ===== 参数 ===== */
    private int chunkRadius = 1;
    private double damage = 2.0;
    private int interval = 20;
    private boolean announce = true;
    private boolean glowing = false;
    private boolean slow = false;
    private int slowAmplifier = 254;
    private boolean enabled = true;

    public WardSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /* ===== getter / setter ===== */
    public int getChunkRadius() { return chunkRadius; }
    public double getDamage() { return damage; }
    public int getInterval() { return interval; }
    public boolean isAnnounce() { return announce; }
    public boolean isGlowing() { return glowing; }
    public boolean isSlow() { return slow; }
    public int getSlowAmplifier() { return slowAmplifier; }
    public boolean isEnabled() { return enabled; }

    /** 一次性写入全部参数（来自 GUI 保存） */
    public void applyAll(int radius, double dmg, int itv, boolean announ,
                         boolean glow, boolean slw, int amp, boolean en) {
        this.chunkRadius = Math.max(0, Math.min(4, radius));
        this.damage = Math.max(0, dmg);
        this.interval = Math.max(1, itv);
        this.announce = announ;
        this.glowing = glow;
        this.slow = slw;
        this.slowAmplifier = Math.max(0, Math.min(254, amp));
        this.enabled = en;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /* ===== 存储 ===== */

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); // 保存烹饪槽位
        tag.putInt("chunkRadius", chunkRadius);
        tag.putDouble("damage", damage);
        tag.putInt("interval", interval);
        tag.putBoolean("announce", announce);
        tag.putBoolean("glowing", glowing);
        tag.putBoolean("slow", slow);
        tag.putInt("slowAmplifier", slowAmplifier);
        tag.putBoolean("enabled", enabled);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); // 读取烹饪槽位
        if (tag.contains("chunkRadius")) chunkRadius = tag.getInt("chunkRadius");
        if (tag.contains("damage")) damage = tag.getDouble("damage");
        if (tag.contains("interval")) interval = tag.getInt("interval");
        if (tag.contains("announce")) announce = tag.getBoolean("announce");
        if (tag.contains("glowing")) glowing = tag.getBoolean("glowing");
        if (tag.contains("slow")) slow = tag.getBoolean("slow");
        if (tag.contains("slowAmplifier")) slowAmplifier = tag.getInt("slowAmplifier");
        if (tag.contains("enabled")) enabled = tag.getBoolean("enabled");
    }

    /* ===== 同步 ===== */

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}