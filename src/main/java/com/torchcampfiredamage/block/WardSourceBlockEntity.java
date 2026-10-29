package com.torchcampfiredamage.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.torchcampfiredamage.events.DamageEventHandler;

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

    /* ===== 环上旋转物品 ===== */
    /** 最多 4 个物品（ItemStack，可为空） */
    private final net.minecraft.world.item.ItemStack[] orbitItems =
            new net.minecraft.world.item.ItemStack[]{
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR),
                    net.minecraft.world.item.ItemStack.EMPTY,
                    net.minecraft.world.item.ItemStack.EMPTY,
                    net.minecraft.world.item.ItemStack.EMPTY
            };
    /** 环是否显示 */
    private boolean orbitVisible = true;
    /** 环半径（方块单位，0.3 ~ 2.0） */
    private float orbitRadius = 0.75f;

    public WardSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public WardSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WARD_SOURCE_BE.get(), pos, state);
    }

    /* ===== 注册到伤害处理器（性能关键：避免全局扫描） ===== */

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide) {
            DamageEventHandler.register(level, getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            DamageEventHandler.unregister(level, getBlockPos());
        }
        super.setRemoved();
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

    /**
     * 放置时用配置文件里的「默认参数」初始化本方块。
     * 之后可通过 GUI 单独修改，不会影响其他火把。
     */
    public void applyDefaults() {
        try {
            var c = com.torchcampfiredamage.config.ModConfig.class;
            this.enabled = com.torchcampfiredamage.config.ModConfig.DEFAULT_ENABLED.get();
            this.chunkMode = com.torchcampfiredamage.config.ModConfig.DEFAULT_CHUNK_MODE.get();
            this.chunkRadius = com.torchcampfiredamage.config.ModConfig.DEFAULT_CHUNK_RADIUS.get();
            this.blockRadius = com.torchcampfiredamage.config.ModConfig.DEFAULT_BLOCK_RADIUS.get();
            this.damage = com.torchcampfiredamage.config.ModConfig.DEFAULT_DAMAGE.get();
            this.interval = com.torchcampfiredamage.config.ModConfig.DEFAULT_INTERVAL.get();
            this.glowing = com.torchcampfiredamage.config.ModConfig.DEFAULT_GLOWING.get();
            this.slow = com.torchcampfiredamage.config.ModConfig.DEFAULT_SLOW.get();
            this.announce = com.torchcampfiredamage.config.ModConfig.DEFAULT_ANNOUNCE.get();
            this.teleportDrops = com.torchcampfiredamage.config.ModConfig.DEFAULT_TELEPORT_DROPS.get();
            this.teleportXp = com.torchcampfiredamage.config.ModConfig.DEFAULT_TELEPORT_XP.get();
            setChanged();
        } catch (Throwable ignored) {
            // 配置未加载时保持字段默认值
        }
    }

    /* ===== 环上物品 ===== */

    public net.minecraft.world.item.ItemStack getOrbitItem(int index) {
        if (index < 0 || index >= 4) return net.minecraft.world.item.ItemStack.EMPTY;
        return orbitItems[index];
    }

    public void setOrbitItem(int index, net.minecraft.world.item.ItemStack stack) {
        if (index < 0 || index >= 4) return;
        this.orbitItems[index] = stack == null ? net.minecraft.world.item.ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        sync();
    }

    /** 当前实际显示的物品数量 */
    public int getOrbitCount() {
        int n = 0;
        for (var st : orbitItems) {
            if (!st.isEmpty()) n++;
        }
        return n;
    }

    public boolean isOrbitVisible() { return orbitVisible; }

    public void setOrbitVisible(boolean v) {
        this.orbitVisible = v;
        setChanged();
        sync();
    }

    public float getOrbitRadius() { return orbitRadius; }

    public void setOrbitRadius(float r) {
        this.orbitRadius = Math.max(0.3f, Math.min(2.0f, r));
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 一次性写入全部参数 */
    public void applyAll(boolean enabled, boolean chunkMode, int range, double damage, int interval,
                         boolean glowing, boolean slow, boolean announce,
                         boolean teleportDrops, boolean teleportXp) {
        this.enabled = enabled;
        this.chunkMode = chunkMode;
        if (chunkMode) {
            this.chunkRadius = Math.max(0, Math.min(32, range));
        } else {
            this.blockRadius = Math.max(0, Math.min(512, range));
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
        tag.putBoolean("orbitVisible", orbitVisible);
        tag.putFloat("orbitRadius", orbitRadius);
        net.minecraft.nbt.ListTag orbit = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 4; i++) {
            var st = orbitItems[i];
            if (st.isEmpty()) {
                orbit.add(new CompoundTag());
            } else {
                CompoundTag c = new CompoundTag();
                c.put("item", st.save(registries));
                orbit.add(c);
            }
        }
        tag.put("orbitItems", orbit);
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
        if (tag.contains("orbitVisible")) orbitVisible = tag.getBoolean("orbitVisible");
        if (tag.contains("orbitRadius")) orbitRadius = tag.getFloat("orbitRadius");
        if (tag.contains("orbitItems")) {
            net.minecraft.nbt.ListTag orbit = tag.getList("orbitItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < 4 && i < orbit.size(); i++) {
                CompoundTag c = orbit.getCompound(i);
                if (c.contains("item")) {
                    orbitItems[i] = net.minecraft.world.item.ItemStack.parseOptional(registries, c.getCompound("item"));
                } else {
                    orbitItems[i] = net.minecraft.world.item.ItemStack.EMPTY;
                }
            }
        }
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