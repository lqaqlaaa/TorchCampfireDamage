package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 守卫火源 BlockEntity。
 * <p>
 * 继承 CampfireBlockEntity —— 因此守卫篝火直接获得原版烹饪能力
 * （4 个物品槽、烤制进度、上火/熄灭的光照联动）。
 * 火把不使用这些槽位，无副作用。
 * <p>
 * 另外保存每方块独立的伤害参数。
 */
public class WardSourceBlockEntity extends CampfireBlockEntity {

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

    public WardSourceBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);       // 烹饪槽位
        tag.putBoolean("W_enabled", enabled);
        tag.putBoolean("W_chunkMode", chunkMode);
        tag.putInt("W_chunkRadius", chunkRadius);
        tag.putInt("W_blockRadius", blockRadius);
        tag.putDouble("W_damage", damage);
        tag.putInt("W_interval", interval);
        tag.putBoolean("W_glowing", glowing);
        tag.putBoolean("W_slow", slow);
        tag.putInt("W_slowAmplifier", slowAmplifier);
        tag.putBoolean("W_announce", announce);
        tag.putBoolean("W_dropTp", teleportDrops);
        tag.putBoolean("W_xpTp", teleportXp);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);       // 烹饪槽位
        if (tag.contains("W_enabled")) enabled = tag.getBoolean("W_enabled");
        if (tag.contains("W_chunkMode")) chunkMode = tag.getBoolean("W_chunkMode");
        if (tag.contains("W_chunkRadius")) chunkRadius = tag.getInt("W_chunkRadius");
        if (tag.contains("W_blockRadius")) blockRadius = tag.getInt("W_blockRadius");
        if (tag.contains("W_damage")) damage = tag.getDouble("W_damage");
        if (tag.contains("W_interval")) interval = tag.getInt("W_interval");
        if (tag.contains("W_glowing")) glowing = tag.getBoolean("W_glowing");
        if (tag.contains("W_slow")) slow = tag.getBoolean("W_slow");
        if (tag.contains("W_slowAmplifier")) slowAmplifier = tag.getInt("W_slowAmplifier");
        if (tag.contains("W_announce")) announce = tag.getBoolean("W_announce");
        if (tag.contains("W_dropTp")) teleportDrops = tag.getBoolean("W_dropTp");
        if (tag.contains("W_xpTp")) teleportXp = tag.getBoolean("W_xpTp");
    }
}