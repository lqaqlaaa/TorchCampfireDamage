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

    /* ===== 星环系统（最多 5 个环） ===== */

    /**
     * 单个环的配置。
     *
     * @param item      环上的物品
     * @param radius    距中心距离（0.3 ~ 3.0 方块）
     * @param speed     旋转速度（度/tick，-20 ~ 20）
     * @param angle     起始角度偏移（0 ~ 360 度）
     * @param tiltX     绕 X 轴倾斜（前后倾斜，-180 ~ 180 度）
     * @param tiltY     绕 Y 轴倾斜（左右偏转，-180 ~ 180 度）
     * @param tiltZ     绕 Z 轴倾斜（左右倾斜，-180 ~ 180 度）
     */
    public static final class Ring {
        public net.minecraft.world.item.ItemStack item;
        public float radius;
        public float speed;
        public float angle;
        /** 旧版单轴倾角，仅为兼容旧存档保留，读取时并入 tiltX */
        public float tilt;
        public float tiltX;
        public float tiltY;
        public float tiltZ;

        /* ===== 轨迹外观 ===== */
        /** 颜色（0xRRGGBB） */
        public int color = 0x66DDFF;
        /** 透明度 0~1 */
        public float alpha = 0.55f;
        /** 线宽（方块单位） */
        public float width = 0.030f;
        /** 轨迹样式：0=连续环, 1=拖尾, 2=虚线 */
        public int style = 0;
        /** 虚线/拖尾的间距系数（0.05~1.0） */
        public float spacing = 0.35f;

        public Ring() {
            this.item = net.minecraft.world.item.ItemStack.EMPTY;
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

    /** 硬上限（防止无限增长拖垮渲染），实际可自由增删 */
    public static final int MAX_RINGS = 64;

    private final java.util.List<Ring> rings = new java.util.ArrayList<>();

    /** 中心旋转的物品 */
    private net.minecraft.world.item.ItemStack centerItem =
            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR);
    /** 中心物品大小（0.2 ~ 2.0） */
    private float centerScale = 0.9f;
    /** 中心物品自转速度（度/tick） */
    private float centerSpin = 1.5f;

    /** 环是否显示 */
    private boolean orbitVisible = true;

    {
        // 默认第 1 环：下界之星
        Ring r0 = new Ring();
        r0.item = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR);
        rings.add(r0);
    }

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

    /** 新增一个空环 */
    public boolean addRing() {
        if (rings.size() >= MAX_RINGS) return false;
        rings.add(new Ring());
        setChanged();
        sync();
        return true;
    }

    /** 移除指定环 */
    public void removeRing(int i) {
        if (i < 0 || i >= rings.size()) return;
        rings.remove(i);
        setChanged();
        sync();
    }

    /** 用新列表整体替换（GUI 保存时用） */
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

    public net.minecraft.world.item.ItemStack getCenterItem() { return centerItem; }

    public void setCenterItem(net.minecraft.world.item.ItemStack st) {
        this.centerItem = (st == null) ? net.minecraft.world.item.ItemStack.EMPTY : st.copyWithCount(1);
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
        tag.putFloat("centerScale", centerScale);
        tag.putFloat("centerSpin", centerSpin);
        if (!centerItem.isEmpty()) {
            tag.put("centerItem", centerItem.save(registries));
        }
        net.minecraft.nbt.ListTag ringList = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < rings.size(); i++) {
            Ring r = rings.get(i);
            CompoundTag c = new CompoundTag();
            if (!r.item.isEmpty()) {
                c.put("item", r.item.save(registries));
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
        if (tag.contains("centerScale")) centerScale = tag.getFloat("centerScale");
        if (tag.contains("centerSpin")) centerSpin = tag.getFloat("centerSpin");
        if (tag.contains("centerItem")) {
            centerItem = net.minecraft.world.item.ItemStack.parseOptional(registries, tag.getCompound("centerItem"));
        }
        if (tag.contains("rings")) {
            rings.clear();
            net.minecraft.nbt.ListTag ringList = tag.getList("rings", Tag.TAG_COMPOUND);
            for (int i = 0; i < MAX_RINGS && i < ringList.size(); i++) {
                CompoundTag c = ringList.getCompound(i);
                Ring r = new Ring();
                if (c.contains("item")) {
                    r.item = net.minecraft.world.item.ItemStack.parseOptional(registries, c.getCompound("item"));
                }
                if (c.contains("radius")) r.radius = c.getFloat("radius");
                if (c.contains("speed")) r.speed = c.getFloat("speed");
                if (c.contains("angle")) r.angle = c.getFloat("angle");
                // 三轴倾斜；旧存档只有单轴 "tilt"，兼容并入 tiltX
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