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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 守卫火把范围显示。
 * <p>
 * 因为作用范围在 Y 轴是无限的，所以只在火把所在的水平面上画一个矩形轮廓，
 * 并在四个角加一小段竖线示意「向上向下延伸」。
 * <p>
 * 渲染使用「相机相对坐标 + pushPose(cameraOffset)」，保证框固定在世界坐标不跟随玩家。
 */
@EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
public class RangeOverlay {

    public record RangeBox(int minX, int minZ, int maxX, int maxZ, int planeY, int color) {}

    private record Key(ResourceKey<Level> dim, BlockPos pos) {}

    private static final Map<Key, RangeBox> BOXES = new HashMap<>();

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

    /** 根据区块/方块模式计算水平面上的范围 */
    public static RangeBox makeBox(BlockPos center, boolean chunkMode, int chunkRadius, int blockRadius, int color) {
        int planeY = center.getY();
        if (chunkMode) {
            int cx = SectionPos.blockToSectionCoord(center.getX());
            int cz = SectionPos.blockToSectionCoord(center.getZ());
            int minX = (cx - chunkRadius) * 16;
            int maxX = (cx + chunkRadius + 1) * 16;
            int minZ = (cz - chunkRadius) * 16;
            int maxZ = (cz + chunkRadius + 1) * 16;
            return new RangeBox(minX, minZ, maxX, maxZ, planeY, color);
        } else {
            int r = blockRadius;
            return new RangeBox(
                    center.getX() - r, center.getZ() - r,
                    center.getX() + r + 1, center.getZ() + r + 1,
                    planeY, color);
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
        // 关键：把坐标系平移到世界原点（抵消相机偏移），否则框会跟着玩家走
        pose.pushPose();
        pose.translate(-camX, -camY, -camZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);

        Tesselator tesselator = Tesselator.getInstance();

        Iterator<Map.Entry<Key, RangeBox>> it = BOXES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, RangeBox> e = it.next();
            Key k = e.getKey();

            if (!k.dim().equals(level.dimension())) continue;

            RangeBox b = e.getValue();

            // 火把被拆了 → 移除显示
            if (level.isLoaded(k.pos()) && !(level.getBlockEntity(k.pos()) instanceof
                    com.torchcampfiredamage.block.WardSourceBlockEntity)) {
                it.remove();
                continue;
            }

            int color = b.color();
            float r = ((color >> 16) & 255) / 255f;
            float g = ((color >> 8) & 255) / 255f;
            float bl = (color & 255) / 255f;
            float a = 0.9f;

            BufferBuilder buf = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

            float x1 = b.minX();
            float x2 = b.maxX();
            float z1 = b.minZ();
            float z2 = b.maxZ();
            float y = b.planeY() + 0.02f;   // 略微抬高，避免与地面 z-fighting

            // ===== 平面上的矩形（4 条边）=====
            line(buf, pose, x1, y, z1, x2, y, z1, r, g, bl, a);
            line(buf, pose, x2, y, z1, x2, y, z2, r, g, bl, a);
            line(buf, pose, x2, y, z2, x1, y, z2, r, g, bl, a);
            line(buf, pose, x1, y, z2, x1, y, z1, r, g, bl, a);

            // ===== 网格辅助线（每 16 格一条，方便估距）=====
            float gridStep = (x2 - x1) > 64 ? 32f : 16f;
            for (float gx = x1 + gridStep; gx < x2; gx += gridStep) {
                line(buf, pose, gx, y, z1, gx, y, z2, r, g, bl, a * 0.28f);
            }
            for (float gz = z1 + gridStep; gz < z2; gz += gridStep) {
                line(buf, pose, x1, y, gz, x2, y, gz, r, g, bl, a * 0.28f);
            }

            // ===== 四角短竖线，示意 Y 轴无限延伸 =====
            float h = 3.0f;
            line(buf, pose, x1, y, z1, x1, y + h, z1, r, g, bl, a);
            line(buf, pose, x2, y, z1, x2, y + h, z1, r, g, bl, a);
            line(buf, pose, x2, y, z2, x2, y + h, z2, r, g, bl, a);
            line(buf, pose, x1, y, z2, x1, y + h, z2, r, g, bl, a);
            line(buf, pose, x1, y, z1, x1, y - h, z1, r, g, bl, a * 0.5f);
            line(buf, pose, x2, y, z1, x2, y - h, z1, r, g, bl, a * 0.5f);
            line(buf, pose, x2, y, z2, x2, y - h, z2, r, g, bl, a * 0.5f);
            line(buf, pose, x1, y, z2, x1, y - h, z2, r, g, bl, a * 0.5f);

            MeshData mesh = buf.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }
        }

        pose.popPose();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    private static void line(BufferBuilder buf, PoseStack pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        var mat = pose.last().pose();
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
    }
}