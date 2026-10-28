package com.torchcampfiredamage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 守卫火把范围显示。
 * <p>
 * 平面边框 + 从火把向四周延伸的十字线（方便定位是哪个火把）。
 * 方块用 TRANSLUCENT 渲染层 + 压低 alpha，实现真半透明。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class RangeOverlay {

    /** 范围数据。blocks 为 2 个方块状态（棋盘格交替）。 */
    public record RangeBox(int minX, int minZ, int maxX, int maxZ, int planeY,
                           BlockState blockA, BlockState blockB) {}

    private record Key(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<Key, RangeBox> BOXES = new HashMap<>();

    /** 默认方块（黑白混凝土） */
    public static final Block DEFAULT_A = Blocks.BLACK_CONCRETE;
    public static final Block DEFAULT_B = Blocks.WHITE_CONCRETE;

    /** 默认透明度 */
    public static float alpha = 0.45f;

    public static void set(ResourceKey<Level> dim, BlockPos pos, RangeBox box) {
        BOXES.put(new Key(dim, pos), box);
    }

    public static void remove(ResourceKey<Level> dim, BlockPos pos) {
        BOXES.remove(new Key(dim, pos));
    }

    public static void clear() {
        BOXES.clear();
    }

    public static boolean has(ResourceKey<Level> dim, BlockPos pos) {
        return BOXES.containsKey(new Key(dim, pos));
    }

    public static RangeBox makeBox(BlockPos center, boolean chunkMode, int chunkRadius, int blockRadius,
                                   Block blockA, Block blockB) {
        int planeY = center.getY() + 1;
        BlockState a = blockA.defaultBlockState();
        BlockState b = blockB.defaultBlockState();

        if (chunkMode) {
            int cx = SectionPos.blockToSectionCoord(center.getX());
            int cz = SectionPos.blockToSectionCoord(center.getZ());
            return new RangeBox(
                    (cx - chunkRadius) * 16, (cz - chunkRadius) * 16,
                    (cx + chunkRadius + 1) * 16, (cz + chunkRadius + 1) * 16,
                    planeY, a, b);
        } else {
            int r = blockRadius;
            return new RangeBox(
                    center.getX() - r, center.getZ() - r,
                    center.getX() + r + 1, center.getZ() + r + 1,
                    planeY, a, b);
        }
    }

    /* ================= 渲染 ================= */

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (BOXES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Camera cam = event.getCamera();
        PoseStack pose = event.getPoseStack();

        pose.pushPose();
        pose.translate(-cam.getPosition().x, -cam.getPosition().y, -cam.getPosition().z);

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RandomSource random = RandomSource.create(42L);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Iterator<Map.Entry<Key, RangeBox>> it = BOXES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, RangeBox> en = it.next();
            Key k = en.getKey();
            if (!k.dim().equals(level.dimension())) continue;

            RangeBox b = en.getValue();

            if (level.isLoaded(k.pos()) && !(level.getBlockEntity(k.pos()) instanceof
                    com.torchcampfiredamage.block.WardSourceBlockEntity)) {
                it.remove();
                continue;
            }

            drawRange(dispatcher, buffers, pose, level, k.pos(), b, random);
        }

        buffers.endBatch();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** 画边框 + 十字线 */
    private static void drawRange(BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                  PoseStack pose, Level level, BlockPos torchPos,
                                  RangeBox b, RandomSource random) {
        int x1 = b.minX(), x2 = b.maxX() - 1;
        int z1 = b.minZ(), z2 = b.maxZ() - 1;
        int y = b.planeY();

        // ===== 1. 边框（一圈）=====
        for (int x = x1; x <= x2; x++) {
            ghost(dispatcher, buffers, pose, level, x, y, z1, pick(b, x, z1), random);
            ghost(dispatcher, buffers, pose, level, x, y, z2, pick(b, x, z2), random);
        }
        for (int z = z1 + 1; z <= z2 - 1; z++) {
            ghost(dispatcher, buffers, pose, level, x1, y, z, pick(b, x1, z), random);
            ghost(dispatcher, buffers, pose, level, x2, y, z, pick(b, x2, z), random);
        }

        // ===== 2. 从火把向四周延伸的十字线（方便找火把位置）=====
        int tx = torchPos.getX();
        int tz = torchPos.getZ();

        // 朝 -X 方向
        for (int x = x1; x < tx; x++) {
            ghost(dispatcher, buffers, pose, level, x, y, tz, pick(b, x, tz), random);
        }
        // 朝 +X 方向
        for (int x = tx + 1; x <= x2; x++) {
            ghost(dispatcher, buffers, pose, level, x, y, tz, pick(b, x, tz), random);
        }
        // 朝 -Z 方向
        for (int z = z1; z < tz; z++) {
            ghost(dispatcher, buffers, pose, level, tx, y, z, pick(b, tx, z), random);
        }
        // 朝 +Z 方向
        for (int z = tz + 1; z <= z2; z++) {
            ghost(dispatcher, buffers, pose, level, tx, y, z, pick(b, tx, z), random);
        }
    }

    private static BlockState pick(RangeBox b, int x, int z) {
        return (((x + z) & 1) == 0) ? b.blockA() : b.blockB();
    }

    /** 渲染半透明幽灵方块 */
    private static void ghost(BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                              PoseStack pose, Level level, int x, int y, int z,
                              BlockState state, RandomSource random) {
        pose.pushPose();
        pose.translate(x, y, z);

        VertexConsumer vc = new AlphaVertexConsumer(
                buffers.getBuffer(RenderType.translucent()), alpha);

        dispatcher.getModelRenderer().tesselateBlock(
                level,
                dispatcher.getBlockModel(state),
                state,
                BlockPos.ZERO,
                pose,
                vc,
                true,
                random,
                state.getSeed(BlockPos.ZERO),
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                RenderType.translucent());

        pose.popPose();
    }

    /** 包装 VertexConsumer，压低 alpha */
    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float a;

        AlphaVertexConsumer(VertexConsumer delegate, float a) {
            this.delegate = delegate;
            this.a = a;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int alpha) {
            delegate.setColor(r, g, b, (int) (alpha * a));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            int r = (color >> 16) & 255;
            int g = (color >> 8) & 255;
            int b = color & 255;
            delegate.setColor(r, g, b, (int) (255 * a));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}