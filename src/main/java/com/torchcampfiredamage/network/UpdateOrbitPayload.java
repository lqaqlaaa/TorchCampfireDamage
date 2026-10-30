package com.torchcampfiredamage.network;

import com.torchcampfiredamage.TorchCampfireDamage;
import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：保存魔法球的星环设置（最多 5 环）。
 */
public record UpdateOrbitPayload(
        BlockPos pos,
        boolean visible,
        ItemStack centerItem,
        float centerScale,
        float centerSpin,
        RingData[] rings
) implements CustomPacketPayload {

    /** 单环数据（tiltX/Y/Z 为三轴倾斜） */
    public record RingData(ItemStack item, float radius, float speed, float angle,
                           float tiltX, float tiltY, float tiltZ,
                           int color, float alpha, float width, int style, float spacing) {}

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(TorchCampfireDamage.MOD_ID, "update_orbit");

    public static final Type<UpdateOrbitPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateOrbitPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public UpdateOrbitPayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    boolean vis = buf.readBoolean();
                    ItemStack centerIt = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    float cScale = buf.readFloat();
                    float cSpin = buf.readFloat();
                    int n = buf.readVarInt();
                    RingData[] arr = new RingData[n];
                    for (int i = 0; i < n; i++) {
                        ItemStack st = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                        float r = buf.readFloat();
                        float s = buf.readFloat();
                        float a = buf.readFloat();
                        float tx = buf.readFloat();
                        float ty = buf.readFloat();
                        float tz = buf.readFloat();
                        int col = buf.readVarInt();
                        float al = buf.readFloat();
                        float wd = buf.readFloat();
                        int sty = buf.readVarInt();
                        float sp = buf.readFloat();
                        arr[i] = new RingData(st, r, s, a, tx, ty, tz, col, al, wd, sty, sp);
                    }
                    return new UpdateOrbitPayload(pos, vis, centerIt, cScale, cSpin, arr);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, UpdateOrbitPayload p) {
                    buf.writeBlockPos(p.pos());
                    buf.writeBoolean(p.visible());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,
                            p.centerItem() == null ? ItemStack.EMPTY : p.centerItem());
                    buf.writeFloat(p.centerScale());
                    buf.writeFloat(p.centerSpin());
                    RingData[] arr = p.rings() == null ? new RingData[0] : p.rings();
                    buf.writeVarInt(arr.length);
                    for (RingData rd : arr) {
                        ItemStack st = (rd == null || rd.item() == null) ? ItemStack.EMPTY : rd.item();
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, st);
                        buf.writeFloat(rd == null ? 0.8f : rd.radius());
                        buf.writeFloat(rd == null ? 2.0f : rd.speed());
                        buf.writeFloat(rd == null ? 0f : rd.angle());
                        buf.writeFloat(rd == null ? 0f : rd.tiltX());
                        buf.writeFloat(rd == null ? 0f : rd.tiltY());
                        buf.writeFloat(rd == null ? 0f : rd.tiltZ());
                        buf.writeVarInt(rd == null ? 0x66DDFF : rd.color());
                        buf.writeFloat(rd == null ? 0.55f : rd.alpha());
                        buf.writeFloat(rd == null ? 0.03f : rd.width());
                        buf.writeVarInt(rd == null ? 0 : rd.style());
                        buf.writeFloat(rd == null ? 0.35f : rd.spacing());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UpdateOrbitPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            BlockPos pos = p.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof WardSourceBlockEntity ward)) return;

            ward.setOrbitVisible(p.visible());
            ward.setCenterItem(p.centerItem() == null ? ItemStack.EMPTY : p.centerItem());
            ward.setCenterScale(p.centerScale());
            ward.setCenterSpin(p.centerSpin());

            RingData[] arr = p.rings();
            if (arr == null) arr = new RingData[0];

            // 关键：先整体清空，再逐条重建，保证删除生效
            var list = new java.util.ArrayList<WardSourceBlockEntity.Ring>();
            int limit = Math.min(WardSourceBlockEntity.MAX_RINGS, arr.length);
            for (int i = 0; i < limit; i++) {
                RingData rd = arr[i];
                WardSourceBlockEntity.Ring ring = new WardSourceBlockEntity.Ring();
                if (rd != null) {
                    ring.item = (rd.item() == null) ? ItemStack.EMPTY : rd.item().copyWithCount(1);
                    ring.radius = rd.radius();
                    ring.speed = rd.speed();
                    ring.angle = rd.angle();
                    ring.tiltX = rd.tiltX();
                    ring.tiltY = rd.tiltY();
                    ring.tiltZ = rd.tiltZ();
                    ring.color = rd.color();
                    ring.alpha = rd.alpha();
                    ring.width = rd.width();
                    ring.style = rd.style();
                    ring.spacing = rd.spacing();
                }
                list.add(ring);
            }
            ward.setRings(list);
        });
    }
}