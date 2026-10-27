package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 守卫火源 BlockEntity —— 所有参数都存在方块自身，跟着方块走。
 */
public class WardSourceBlockEntity extends BlockEntity {

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

    /* ===== getter ===== */
    public int getChunkRadius() { return chunkRadius; }
    public double getDamage() { return damage; }
    public int getInterval() { return interval; }
    public boolean isAnnounce() { return announce; }
    public boolean isGlowing() { return glowing; }
    public boolean isSlow() { return slow; }
    public int getSlowAmplifier() { return slowAmplifier; }
    public boolean isEnabled() { return enabled; }

    /** 一次性写入全部参数 */
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("chunkRadius")) chunkRadius = tag.getInt("chunkRadius");
        if (tag.contains("damage")) damage = tag.getDouble("damage");
        if (tag.contains("interval")) interval = tag.getInt("interval");
        if (tag.contains("announce")) announce = tag.getBoolean("announce");
        if (tag.contains("glowing")) glowing = tag.getBoolean("glowing");
        if (tag.contains("slow")) slow = tag.getBoolean("slow");
        if (tag.contains("slowAmplifier")) slowAmplifier = tag.getInt("slowAmplifier");
        if (tag.contains("enabled")) enabled = tag.getBoolean("enabled");
    }

    /* ===== 客户端同步 ===== */

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