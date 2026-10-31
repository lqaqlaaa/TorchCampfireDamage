package com.torchcampfiredamage.block;

import com.torchcampfiredamage.config.ModConfig;
import com.torchcampfiredamage.events.DamageEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 守卫火源 BlockEntity —— 参数存在方块自身，跟着方块走。
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
    /** 是否忽略被命名的生物（白名单保护），本火把独立开关 */
    private boolean ignoreNamed = true;

    /* ===== 星环系统 ===== */

    /**
     * 单个环的配置。
     */
    public static final class Ring {
        public ItemStack item;
        public float radius;
        public float speed;
        public float angle;
        /** 旧版单轴倾角，仅为兼容旧存档保留 */
        public float tilt;
        /** 绕 X 轴（前后俯仰） */
        public float tiltX;
        /** 绕 Y 轴（水平自转） */
        public float tiltY;
        /** 绕 Z 轴（左右侧倾） */
        public float tiltZ;

        /* 轨迹外观 */
        public int color = 0x66DDFF;
        public float alpha = 0.55f;
        public float width = 0.030f;
        /** 0=连续环, 1=拖尾, 2=虚线 */
        public int style = 0;
        public float spacing = 0.35f;

        public Ring() {
            this.item = ItemStack.EMPTY;
            this.radius = 0.8f;
            this.speed = 2.0f;
            this.angle = 0f;
            this.tilt = 0f;
            this.tiltX = 0f;
            this.tiltY = 0f;
            this.tiltZ = 0f;
        }

        public Ring copy() {
            Ring r = new Ring();
            r.item = this.item.copy();
            r.radius = this.radius;
            r.speed = this.speed;
            r.angle = this.angle;
            r.tilt = this.tilt;
            r.tiltX = this.tiltX;
            r.tiltY = this.tiltY;
            r.tiltZ = this.tiltZ;
            r.color = this.color;
            r.alpha = this.alpha;
            r.width = this.width;
            r.style = this.style;
            r.spacing = this.spacing;
            return r;
        }
    }

    public static final int MAX_RINGS = 64;

    private final java.util.List<Ring> rings = new java.util.ArrayList<>();

    private ItemStack centerItem = new ItemStack(Items.NETHER_STAR);
    private float centerScale = 0.9f;
    private float centerSpin = 1.5f;

    private boolean orbitVisible = true;

    {
        Ring r0 = new Ring();
        r0.item = new ItemStack(Items.NETHER_STAR);
        rings.add(r0);
    }

    public WardSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public WardSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WARD_SOURCE_BE.get(), pos, state);
    }

    /* ===== 注册到伤害处理器 ===== */

    @Override
    public void setLevel(Level level) {
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
    public boolean isIgnoreNamed() { return ignoreNamed; }
    public void setIgnoreNamed(boolean v) { this.ignoreNamed = v; setChanged(); }

    /**
     * 放置时用配置文件里的「默认参数」初始化本方块。
     */
    public void applyDefaults() {
        try {
            this.enabled = ModConfig.DEFAULT_ENABLED.get();
            this.chunkMode = ModConfig.DEFAULT_CHUNK_MODE.get();
            this.chunkRadius = ModConfig.DEFAULT_CHUNK_RADIUS.get();
            this.blockRadius = ModConfig.DEFAULT_BLOCK_RADIUS.get();
            this.damage = ModConfig.DEFAULT_DAMAGE.get();
            this.interval = ModConfig.DEFAULT_INTERVAL.get();
            this.glowing = ModConfig.DEFAULT_GLOWING.get();
            this.slow = ModConfig.DEFAULT_SLOW.get();
            this.announce = ModConfig.DEFAULT_ANNOUNCE.get();
            this.teleportDrops = ModConfig.DEFAULT_TELEPORT_DROPS.get();
            this.teleportXp = ModConfig.DEFAULT_TELEPORT_XP.get();
            this.ignoreNamed = ModConfig.IGNORE_NAMED_MOBS.get();
            setChanged();
        } catch (Throwable ignored) {
            // 配置未加载时保持字段默认值
        }
    }

    /* ===== 星环访问器 ===== */

    public int getRingCount() { return rings.size(); }

    public Ring getRing(int i) {
        if (i < 0 || i >= rings.size()) return new Ring();
        return rings.get(i);
    }

    public void setRing(int i, Ring r) {
        if (i < 0 || r == null) return;
        if (i < rings.size()) {
            rings.set(i, r.copy());
        } else if (i == rings.size() && rings.size() < MAX_RINGS) {
            rings.add(r.copy());
        } else {
            return;
        }
        setChanged();
        sync();
    }

    public boolean addRing() {
        if (rings.size() >= MAX_RINGS) return false;
        rings.add(new Ring());
        setChanged();
        sync();
        return true;
    }

    public void removeRing(int i) {
        if (i < 0 || i >= rings.size()) return;
        rings.remove(i);
        setChanged();
        sync();
    }

    public void setRings(java.util.List<Ring> list) {
        rings.clear();
        if (list != null) {
            for (Ring r : list) {
                if (rings.size() >= MAX_RINGS) break;
                rings.add(r.copy());
            }
        }
        setChanged();
        sync();
    }

    public ItemStack getCenterItem() { return centerItem; }

    public void setCenterItem(ItemStack st) {
        this.centerItem = (st == null) ? ItemStack.EMPTY : st.copyWithCount(1);
        setChanged();
        sync();
    }

    public float getCenterScale() { return centerScale; }

    public void setCenterScale(float v) {
        this.centerScale = Math.max(0.2f, Math.min(2.0f, v));
        setChanged();
        sync();
    }

    public float getCenterSpin() { return centerSpin; }

    public void setCenterSpin(float v) {
        this.centerSpin = Math.max(-20f, Math.min(20f, v));
        setChanged();
        sync();
    }

    public boolean isOrbitVisible() { return orbitVisible; }

    public void setOrbitVisible(boolean v) {
        this.orbitVisible = v;
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
                         boolean teleportDrops, boolean teleportXp, boolean ignoreNamed) {
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
        this.ignoreNamed = ignoreNamed;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /* ===== 存储（1.20.1 没有 registries 参数） ===== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
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
        tag.putBoolean("ignoreNamed", ignoreNamed);
        tag.putBoolean("orbitVisible", orbitVisible);
        tag.putFloat("centerScale", centerScale);
        tag.putFloat("centerSpin", centerSpin);
        if (!centerItem.isEmpty()) {
            tag.put("centerItem", centerItem.save(new CompoundTag()));
        }
        ListTag ringList = new ListTag();
        for (int i = 0; i < rings.size(); i++) {
            Ring r = rings.get(i);
            CompoundTag c = new CompoundTag();
            if (!r.item.isEmpty()) {
                c.put("item", r.item.save(new CompoundTag()));
            }
            c.putFloat("radius", r.radius);
            c.putFloat("speed", r.speed);
            c.putFloat("angle", r.angle);
            c.putFloat("tiltX", r.tiltX);
            c.putFloat("tiltY", r.tiltY);
            c.putFloat("tiltZ", r.tiltZ);
            c.putInt("color", r.color);
            c.putFloat("alpha", r.alpha);
            c.putFloat("width", r.width);
            c.putInt("style", r.style);
            c.putFloat("spacing", r.spacing);
            ringList.add(c);
        }
        tag.put("rings", ringList);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
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
        // 旧存档没有这个字段时，回落到全局配置
        if (tag.contains("ignoreNamed")) {
            ignoreNamed = tag.getBoolean("ignoreNamed");
        } else {
            try {
                ignoreNamed = ModConfig.IGNORE_NAMED_MOBS.get();
            } catch (Throwable ignored) {
            }
        }
        if (tag.contains("orbitVisible")) orbitVisible = tag.getBoolean("orbitVisible");
        if (tag.contains("centerScale")) centerScale = tag.getFloat("centerScale");
        if (tag.contains("centerSpin")) centerSpin = tag.getFloat("centerSpin");
        if (tag.contains("centerItem")) {
            centerItem = ItemStack.of(tag.getCompound("centerItem"));
        }
        if (tag.contains("rings")) {
            rings.clear();
            ListTag ringList = tag.getList("rings", Tag.TAG_COMPOUND);
            for (int i = 0; i < MAX_RINGS && i < ringList.size(); i++) {
                CompoundTag c = ringList.getCompound(i);
                Ring r = new Ring();
                if (c.contains("item")) {
                    r.item = ItemStack.of(c.getCompound("item"));
                }
                if (c.contains("radius")) r.radius = c.getFloat("radius");
                if (c.contains("speed")) r.speed = c.getFloat("speed");
                if (c.contains("angle")) r.angle = c.getFloat("angle");
                if (c.contains("tiltX")) r.tiltX = c.getFloat("tiltX");
                else if (c.contains("tilt")) r.tiltX = c.getFloat("tilt");
                if (c.contains("tiltY")) r.tiltY = c.getFloat("tiltY");
                if (c.contains("tiltZ")) r.tiltZ = c.getFloat("tiltZ");
                if (c.contains("color")) r.color = c.getInt("color");
                if (c.contains("alpha")) r.alpha = c.getFloat("alpha");
                if (c.contains("width")) r.width = c.getFloat("width");
                if (c.contains("style")) r.style = c.getInt("style");
                if (c.contains("spacing")) r.spacing = c.getFloat("spacing");
                rings.add(r);
            }
        }
    }

    /* ===== 同步 ===== */

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}