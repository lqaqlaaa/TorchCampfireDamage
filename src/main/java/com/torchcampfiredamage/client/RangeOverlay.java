package com.torchcampfiredamage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
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
 * 守卫火把范围显示 —— 真方块渲染（参考 Litematica 思路的轻量版）。
 * <p>
 * 在火把所在的水平面上，用黑白混凝土方块铺出棋盘格，直观展示作用范围。
 * Y 轴只铺 1 层（因为高度无限）。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class RangeOverlay {

    public record RangeBox(int minX, int minZ, int maxX, int maxZ, int planeY) {}

    private record Key(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<Key, RangeBox> BOXES = new HashMap<>();

    /** 显示用的两种方块（黑白混凝土） */
    private static final BlockState BLOCK_A = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState BLOCK_B = Blocks.WHITE_CONCRETE.defaultBlockState();

    /** 平面显示高度（相对火把 Y） */
    private static final int PLANE_OFFSET = 1;

    /** 单次最多铺多少格，防止超大范围卡死 */
    private static final int MAX_CELLS = 20000;

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

    public static RangeBox makeBox(BlockPos center, boolean chunkMode, int chunkRadius, int blockRadius) {
        int planeY = center.getY() + PLANE_OFFSET;
        if (chunkMode) {
            int cx = SectionPos.blockToSectionCoord(center.getX());
            int cz = SectionPos.blockToSectionCoord(center.getZ());
            int minX = (cx - chunkRadius) * 16;
            int maxX = (cx + chunkRadius + 1) * 16;
            int minZ = (cz - chunkRadius) * 16;
            int maxZ = (cz + chunkRadius + 1) * 16;
            return new RangeBox(minX, minZ, maxX, maxZ, planeY);
        } else {
            int r = blockRadius;
            return new RangeBox(
                    center.getX() - r, center.getZ() - r,
                    center.getX() + r + 1, center.getZ() + r + 1,
                    planeY);
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
        double camX = cam.getPosition().x;
        double camY = cam.getPosition().y;
        double camZ = cam.getPosition().z;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // 平移到世界原点，避免跟随相机
        pose.translate(-camX, -camY, -camZ);

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        RandomSource random = RandomSource.create(42L);

        // 半透明
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        Iterator<Map.Entry<Key, RangeBox>> it = BOXES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, RangeBox> e = it.next();
            Key k = e.getKey();

            if (!k.dim().equals(level.dimension())) continue;

            RangeBox b = e.getValue();

            // 火把被拆 → 移除
            if (level.isLoaded(k.pos()) && !(level.getBlockEntity(k.pos()) instanceof
                    com.torchcampfiredamage.block.WardSourceBlockEntity)) {
                it.remove();
                continue;
            }

            int w = b.maxX() - b.minX();
            int d = b.maxZ() - b.minZ();
            if ((long) w * d > MAX_CELLS) {
                // 范围过大，只画外圈一两环，避免卡死
                if (!drawBorderOnly(dispatcher, buffers, pose, level, b)) {
                    continue;
                }
                continue;
            }

            for (int x = b.minX(); x < b.maxX(); x++) {
                for (int z = b.minZ(); z < b.maxZ(); z++) {
                    // 棋盘格：黑白交替
                    BlockState state = (((x + z) & 1) == 0) ? BLOCK_A : BLOCK_B;
                    drawBlock(dispatcher, buffers, pose, level, x, b.planeY(), z, state, random);
                }
            }
        }

        buffers.endBatch();

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        pose.popPose();
    }

    /** 范围太大时，只画最外一圈（棋盘格） */
    private static boolean drawBorderOnly(BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                          PoseStack pose, Level level, RangeBox b) {
        RandomSource random = RandomSource.create(7L);
        for (int x = b.minX(); x < b.maxX(); x++) {
            BlockState s1 = (((x + b.minZ()) & 1) == 0) ? BLOCK_A : BLOCK_B;
            drawBlock(dispatcher, buffers, pose, level, x, b.planeY(), b.minZ(), s1, random);

            BlockState s2 = (((x + b.maxZ() - 1) & 1) == 0) ? BLOCK_A : BLOCK_B;
            drawBlock(dispatcher, buffers, pose, level, x, b.planeY(), b.maxZ() - 1, s2, random);
        }
        for (int z = b.minZ() + 1; z < b.maxZ() - 1; z++) {
            BlockState s1 = (((b.minX() + z) & 1) == 0) ? BLOCK_A : BLOCK_B;
            drawBlock(dispatcher, buffers, pose, level, b.minX(), b.planeY(), z, s1, random);

            BlockState s2 = (((b.maxX() - 1 + z) & 1) == 0) ? BLOCK_A : BLOCK_B;
            drawBlock(dispatcher, buffers, pose, level, b.maxX() - 1, b.planeY(), z, s2, random);
        }
        return true;
    }

    /** 渲染单个方块（半透明） */
    private static void drawBlock(BlockRenderDispatcher dispatcher, MultiBufferSource buffers,
                                  PoseStack pose, Level level, int x, int y, int z,
                                  BlockState state, RandomSource random) {
        pose.pushPose();
        pose.translate(x, y, z);

        for (RenderType type : ItemBlockRenderTypes.getRenderLayers(state)) {
            VertexConsumer vc = buffers.getBuffer(type);
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
                    type);
        }

        pose.popPose();
    }
}