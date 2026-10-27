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
 * 守卫火源 BlockEntity —— 参数存在方块自身，跟着方块走。
 * <p>
 * 注意：不能继承原版 CampfireBlockEntity，因为它在构造时校验
 * 方块必须是 minecraft:campfire，自定义方块会抛 IllegalStateException。
 * 所以这里用普通 BlockEntity，只保存伤害参数。
 */
public class WardSourceBlockEntity extends BlockEntity {

    private boolean enabled = true;
    /** true = 区块模式, false = 方块模式 */
    private boolean chunkMode = true;
    private int chunkRadius = 1;
    private int blockRadius = 16;
    private double damage = 2.0;
    private int interval = 20;
    private boolean glowing = false;
    private boolean slow = false;
    private int slowAmplifier = 254;
    private boolean announce = true;
    private boolean teleportDrops = true;
    private boolean teleportXp = true;

    public WardSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public WardSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WARD_SOURCE_BE.get(), pos, state);
    }

    /* ===== getter ===== */
    public boolean isEnabled() { return enabled; }
    public boolean isChunkMode() { return chunkMode; }
    public int getChunkRadius() { return chunkRadius; }
    public int getBlockRadius() { return blockRadius; }
    public double getDamage() { return damage; }
    public int getInterval() { return interval; }
    public boolean isGlowing() { return glowing; }
    public boolean isSlow() { return slow; }
    public int getSlowAmplifier() { return slowAmplifier; }
    public boolean isAnnounce() { return announce; }
    public boolean isTeleportDrops() { return teleportDrops; }
    public boolean isTeleportXp() { return teleportXp; }

    /** 一次性写入全部参数 */
    public void applyAll(boolean enabled, boolean chunkMode, int range, double damage, int interval,
                         boolean glowing, boolean slow, boolean announce,
                         boolean teleportDrops, boolean teleportXp) {
        this.enabled = enabled;
        this.chunkMode = chunkMode;
        if (chunkMode) {
            this.chunkRadius = Math.max(0, Math.min(4, range));
        } else {
            this.blockRadius = Math.max(0, Math.min(128, range));
        }
        this.damage = Math.max(0, damage);
        this.interval = Math.max(1, interval);
        this.glowing = glowing;
        this.slow = slow;
        this.announce = announce;
        this.teleportDrops = teleportDrops;
        this.teleportXp = teleportXp;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /* ===== 存储 ===== */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("chunkMode", chunkMode);
        tag.putInt("chunkRadius", chunkRadius);
        tag.putInt("blockRadius", blockRadius);
        tag.putDouble("damage", damage);
        tag.putInt("interval", interval);
        tag.putBoolean("glowing", glowing);
        tag.putBoolean("slow", slow);
        tag.putInt("slowAmplifier", slowAmplifier);
        tag.putBoolean("announce", announce);
        tag.putBoolean("teleportDrops", teleportDrops);
        tag.putBoolean("teleportXp", teleportXp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("enabled")) enabled = tag.getBoolean("enabled");
        if (tag.contains("chunkMode")) chunkMode = tag.getBoolean("chunkMode");
        if (tag.contains("chunkRadius")) chunkRadius = tag.getInt("chunkRadius");
        if (tag.contains("blockRadius")) blockRadius = tag.getInt("blockRadius");
        if (tag.contains("damage")) damage = tag.getDouble("damage");
        if (tag.contains("interval")) interval = tag.getInt("interval");
        if (tag.contains("glowing")) glowing = tag.getBoolean("glowing");
        if (tag.contains("slow")) slow = tag.getBoolean("slow");
        if (tag.contains("slowAmplifier")) slowAmplifier = tag.getInt("slowAmplifier");
        if (tag.contains("announce")) announce = tag.getBoolean("announce");
        if (tag.contains("teleportDrops")) teleportDrops = tag.getBoolean("teleportDrops");
        if (tag.contains("teleportXp")) teleportXp = tag.getBoolean("teleportXp");
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