package com.torchcampfiredamage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 守卫火把范围显示。
 * <p>
 * 只绘制线框立方体（性能友好），用于在游戏中直观看到火把的作用范围。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class RangeOverlay {

    /**
     * 显示中的范围框。
     *
     * @param minX 最小 X（含）
     * @param minY 最小 Y（含）
     * @param minZ 最小 Z（含）
     * @param maxX 最大 X（不含）
     * @param maxY 最大 Y（不含）
     * @param maxZ 最大 Z（不含）
     */
    public record RangeBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color) {}

    private record Key(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<Key, RangeBox> BOXES = new HashMap<>();

    /** 由客户端在打开/关闭 GUI 时调用 */
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

    /** 根据区块/方块模式计算显示用的范围盒 */
    public static RangeBox makeBox(BlockPos center, boolean chunkMode, int chunkRadius, int blockRadius, int color) {
        if (chunkMode) {
            int cx = SectionPos.blockToSectionCoord(center.getX());
            int cz = SectionPos.blockToSectionCoord(center.getZ());
            int minX = (cx - chunkRadius) * 16;
            int maxX = (cx + chunkRadius + 1) * 16;
            int minZ = (cz - chunkRadius) * 16;
            int maxZ = (cz + chunkRadius + 1) * 16;
            // Y 轴与实际伤害范围一致：±64
            return new RangeBox(minX, center.getY() - 64, minZ, maxX, center.getY() + 64, maxZ, color);
        } else {
            int r = blockRadius;
            int ry = Math.min(r, 64);
            return new RangeBox(
                    center.getX() - r, center.getY() - ry, center.getZ() - r,
                    center.getX() + r + 1, center.getY() + ry + 1, center.getZ() + r + 1,
                    color);
        }
    }

    /* ================= 渲染 ================= */

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (BOXES.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        Camera cam = event.getCamera();
        double camX = cam.getPosition().x;
        double camY = cam.getPosition().y;
        double camZ = cam.getPosition().z;
        int renderDist = mc.options.getEffectiveRenderDistance() * 16;

        PoseStack pose = event.getPoseStack();
        Matrix4f mat = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();

        Iterator<Map.Entry<Key, RangeBox>> it = BOXES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, RangeBox> e = it.next();
            Key k = e.getKey();

            // 不同维度不画
            if (!k.dim().equals(level.dimension())) continue;

            RangeBox b = e.getValue();

            // 火把不存在了 → 移除
            if (level.isLoaded(k.pos()) && !(level.getBlockEntity(k.pos()) instanceof
                    com.torchcampfiredamage.block.WardSourceBlockEntity)) {
                it.remove();
                continue;
            }

            // 距离裁剪
            double dx = k.pos().getX() - camX;
            double dz = k.pos().getZ() - camZ;
            if (dx * dx + dz * dz > (double) (renderDist + Math.max(b.maxX() - b.minX(), b.maxZ() - b.minZ())) *
                    (renderDist + Math.max(b.maxX() - b.minX(), b.maxZ() - b.minZ()))) {
                continue;
            }

            int color = b.color();
            float r = ((color >> 16) & 255) / 255f;
            float g = ((color >> 8) & 255) / 255f;
            float bl = (color & 255) / 255f;
            float a = 0.85f;

            BufferBuilder buf = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

            float x1 = (float) (b.minX() - camX);
            float y1 = (float) (b.minY() - camY);
            float z1 = (float) (b.minZ() - camZ);
            float x2 = (float) (b.maxX() - camX);
            float y2 = (float) (b.maxY() - camY);
            float z2 = (float) (b.maxZ() - camZ);

            // 底部 4 条
            line(buf, mat, x1, y1, z1, x2, y1, z1, r, g, bl, a);
            line(buf, mat, x2, y1, z1, x2, y1, z2, r, g, bl, a);
            line(buf, mat, x2, y1, z2, x1, y1, z2, r, g, bl, a);
            line(buf, mat, x1, y1, z2, x1, y1, z1, r, g, bl, a);
            // 顶部 4 条
            line(buf, mat, x1, y2, z1, x2, y2, z1, r, g, bl, a);
            line(buf, mat, x2, y2, z1, x2, y2, z2, r, g, bl, a);
            line(buf, mat, x2, y2, z2, x1, y2, z2, r, g, bl, a);
            line(buf, mat, x1, y2, z2, x1, y2, z1, r, g, bl, a);
            // 竖直 4 条
            line(buf, mat, x1, y1, z1, x1, y2, z1, r, g, bl, a);
            line(buf, mat, x2, y1, z1, x2, y2, z1, r, g, bl, a);
            line(buf, mat, x2, y1, z2, x2, y2, z2, r, g, bl, a);
            line(buf, mat, x1, y1, z2, x1, y2, z2, r, g, bl, a);

            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void line(BufferBuilder buf, Matrix4f mat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
    }
}