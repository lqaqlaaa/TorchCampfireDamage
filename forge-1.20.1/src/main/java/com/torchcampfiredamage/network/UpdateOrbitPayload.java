package com.torchcampfiredamage.network;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：保存星环设置。
 */
public class UpdateOrbitPayload {

    /** 单环数据 */
    public static final class RingData {
        public ItemStack item;
        public float radius;
        public float speed;
        public float angle;
        public float tiltX;
        public float tiltY;
        public float tiltZ;
        public int color;
        public float alpha;
        public float width;
        public int style;
        public float spacing;

        public RingData() {
        }

        public RingData(ItemStack item, float radius, float speed, float angle,
                        float tiltX, float tiltY, float tiltZ,
                        int color, float alpha, float width, int style, float spacing) {
            this.item = item;
            this.radius = radius;
            this.speed = speed;
            this.angle = angle;
            this.tiltX = tiltX;
            this.tiltY = tiltY;
            this.tiltZ = tiltZ;
            this.color = color;
            this.alpha = alpha;
            this.width = width;
            this.style = style;
            this.spacing = spacing;
        }
    }

    private final BlockPos pos;
    private final boolean visible;
    private final ItemStack centerItem;
    private final float centerScale;
    private final float centerSpin;
    private final RingData[] rings;

    public UpdateOrbitPayload(BlockPos pos, boolean visible, ItemStack centerItem,
                              float centerScale, float centerSpin, RingData[] rings) {
        this.pos = pos;
        this.visible = visible;
        this.centerItem = centerItem;
        this.centerScale = centerScale;
        this.centerSpin = centerSpin;
        this.rings = rings;
    }

    /* ================= 编解码 ================= */

    public static void encode(UpdateOrbitPayload p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.visible);
        buf.writeItem(p.centerItem == null ? ItemStack.EMPTY : p.centerItem);
        buf.writeFloat(p.centerScale);
        buf.writeFloat(p.centerSpin);

        RingData[] arr = p.rings == null ? new RingData[0] : p.rings;
        buf.writeVarInt(arr.length);
        for (RingData rd : arr) {
            if (rd == null) {
                buf.writeItem(ItemStack.EMPTY);
                buf.writeFloat(0.8f);
                buf.writeFloat(2.0f);
                buf.writeFloat(0f);
                buf.writeFloat(0f);
                buf.writeFloat(0f);
                buf.writeFloat(0f);
                buf.writeVarInt(0x66DDFF);
                buf.writeFloat(0.55f);
                buf.writeFloat(0.03f);
                buf.writeVarInt(0);
                buf.writeFloat(0.35f);
            } else {
                buf.writeItem(rd.item == null ? ItemStack.EMPTY : rd.item);
                buf.writeFloat(rd.radius);
                buf.writeFloat(rd.speed);
                buf.writeFloat(rd.angle);
                buf.writeFloat(rd.tiltX);
                buf.writeFloat(rd.tiltY);
                buf.writeFloat(rd.tiltZ);
                buf.writeVarInt(rd.color);
                buf.writeFloat(rd.alpha);
                buf.writeFloat(rd.width);
                buf.writeVarInt(rd.style);
                buf.writeFloat(rd.spacing);
            }
        }
    }

    public static UpdateOrbitPayload decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        boolean vis = buf.readBoolean();
        ItemStack centerIt = buf.readItem();
        float cScale = buf.readFloat();
        float cSpin = buf.readFloat();

        int n = buf.readVarInt();
        RingData[] arr = new RingData[n];
        for (int i = 0; i < n; i++) {
            RingData rd = new RingData();
            rd.item = buf.readItem();
            rd.radius = buf.readFloat();
            rd.speed = buf.readFloat();
            rd.angle = buf.readFloat();
            rd.tiltX = buf.readFloat();
            rd.tiltY = buf.readFloat();
            rd.tiltZ = buf.readFloat();
            rd.color = buf.readVarInt();
            rd.alpha = buf.readFloat();
            rd.width = buf.readFloat();
            rd.style = buf.readVarInt();
            rd.spacing = buf.readFloat();
            arr[i] = rd;
        }
        return new UpdateOrbitPayload(pos, vis, centerIt, cScale, cSpin, arr);
    }

    /* ================= 处理 ================= */

    public static void handle(UpdateOrbitPayload p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            BlockPos pos = p.pos;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                return;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WardSourceBlockEntity ward)) return;

            ward.setOrbitVisible(p.visible);
            ward.setCenterItem(p.centerItem == null ? ItemStack.EMPTY : p.centerItem);
            ward.setCenterScale(p.centerScale);
            ward.setCenterSpin(p.centerSpin);

            RingData[] arr = p.rings;
            if (arr == null) arr = new RingData[0];

            java.util.List<WardSourceBlockEntity.Ring> list = new java.util.ArrayList<>();
            int limit = Math.min(WardSourceBlockEntity.MAX_RINGS, arr.length);
            for (int i = 0; i < limit; i++) {
                RingData rd = arr[i];
                WardSourceBlockEntity.Ring ring = new WardSourceBlockEntity.Ring();
                if (rd != null) {
                    ring.item = (rd.item == null) ? ItemStack.EMPTY : rd.item.copyWithCount(1);
                    ring.radius = rd.radius;
                    ring.speed = rd.speed;
                    ring.angle = rd.angle;
                    ring.tiltX = rd.tiltX;
                    ring.tiltY = rd.tiltY;
                    ring.tiltZ = rd.tiltZ;
                    ring.color = rd.color;
                    ring.alpha = rd.alpha;
                    ring.width = rd.width;
                    ring.style = rd.style;
                    ring.spacing = rd.spacing;
                }
                list.add(ring);
            }
            ward.setRings(list);
        });
        ctx.setPacketHandled(true);
    }
}