package com.torchcampfiredamage.network;

import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：保存守卫方块参数。
 * <p>
 * 1.20.1 用 Friend lyByteBuf + 静态 encode/decode/handle。
 */
public class UpdateWardPayload {

    private final BlockPos pos;
    private final boolean enabled;
    private final boolean chunkMode;
    private final int range;
    private final double damage;
    private final int interval;
    private final boolean glowing;
    private final boolean slow;
    private final boolean announce;
    private final boolean teleportDrops;
    private final boolean teleportXp;
    private final boolean ignoreNamed;

    public UpdateWardPayload(BlockPos pos, boolean enabled, boolean chunkMode, int range,
                             double damage, int interval, boolean glowing, boolean slow,
                             boolean announce, boolean teleportDrops, boolean teleportXp,
                             boolean ignoreNamed) {
        this.pos = pos;
        this.enabled = enabled;
        this.chunkMode = chunkMode;
        this.range = range;
        this.damage = damage;
        this.interval = interval;
        this.glowing = glowing;
        this.slow = slow;
        this.announce = announce;
        this.teleportDrops = teleportDrops;
        this.teleportXp = teleportXp;
        this.ignoreNamed = ignoreNamed;
    }

    /* ================= 编解码 ================= */

    public static void encode(UpdateWardPayload p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos);
        buf.writeBoolean(p.enabled);
        buf.writeBoolean(p.chunkMode);
        buf.writeVarInt(p.range);
        buf.writeDouble(p.damage);
        buf.writeVarInt(p.interval);
        buf.writeBoolean(p.glowing);
        buf.writeBoolean(p.slow);
        buf.writeBoolean(p.announce);
        buf.writeBoolean(p.teleportDrops);
        buf.writeBoolean(p.teleportXp);
        buf.writeBoolean(p.ignoreNamed);
    }

    public static UpdateWardPayload decode(FriendlyByteBuf buf) {
        return new UpdateWardPayload(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    /* ================= 处理 ================= */

    public static void handle(UpdateWardPayload p, Supplier<NetworkEvent.Context> ctxSup) {
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

            ward.applyAll(p.enabled, p.chunkMode, p.range, p.damage, p.interval,
                    p.glowing, p.slow, p.announce, p.teleportDrops, p.teleportXp,
                    p.ignoreNamed);
        });
        ctx.setPacketHandled(true);
    }
}