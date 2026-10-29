package com.torchcampfiredamage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.torchcampfiredamage.block.ModBlocks;
import com.torchcampfiredamage.block.WardSourceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

/**
 * 魔法球 + 星环渲染。
 * <p>
 * 轨迹使用「始终面向相机的带状四边形」，任何视角都能看清。
 * 支持 3 种样式：
 *  - 0 连续环（首尾渐隐）
 *  - 1 拖尾（跟随物品，越远越淡）
 *  - 2 虚线（分段，两端渐隐）
 */
public class OrbitRenderHandler {

    @EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
    public static class Registrar {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(
                    ModBlocks.WARD_SOURCE_BE.get(),
                    OrbitRenderer::new);
        }
    }

    public static class OrbitRenderer implements BlockEntityRenderer<WardSourceBlockEntity> {
        private final ItemRenderer itemRenderer;

        public OrbitRenderer(BlockEntityRendererProvider.Context ctx) {
            this.itemRenderer = ctx.getItemRenderer();
        }

        @Override
        public void render(WardSourceBlockEntity be, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light, int overlay) {
            if (!be.isOrbitVisible()) return;

            var level = Minecraft.getInstance().level;
            float time = (level == null ? 0 : level.getGameTime()) + partialTick;

            final double cy = 0.5;

            // 相机位置（世界坐标），用于构建始终面向相机的带状面
            Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            /* ===== 中心物品 ===== */
            ItemStack center = be.getCenterItem();
            if (center != null && !center.isEmpty()) {
                pose.pushPose();
                pose.translate(0.5, cy, 0.5);
                float cbob = Mth.sin(time * 0.08f) * 0.03f;
                pose.translate(0, cbob, 0);
                pose.mulPose(Axis.YP.rotationDegrees(time * be.getCenterSpin()));
                float cs = be.getCenterScale();
                pose.scale(cs, cs, cs);
                itemRenderer.renderStatic(center, ItemDisplayContext.GROUND, light, overlay,
                        pose, buffers, level, 0);
                pose.popPose();
            }

            /* ===== 逐环 ===== */
            int n = be.getRingCount();
            for (int ri = 0; ri < n; ri++) {
                WardSourceBlockEntity.Ring ring = be.getRing(ri);
                ItemStack item = ring.item;
                if (item == null || item.isEmpty()) continue;

                float radius = ring.radius;
                float speed = ring.speed;
                float baseAngle = ring.angle;
                float tilt = ring.tilt;

                float cur = baseAngle + time * speed;
                float curRad = (float) Math.toRadians(cur);

                // ===== 轨迹 =====
                pose.pushPose();
                pose.translate(0.5, cy, 0.5);
                if (tilt != 0f) {
                    pose.mulPose(Axis.XP.rotationDegrees(tilt));
                }

                drawTrail(pose, buffers, ring, curRad, camPos, be, time);

                // ===== 环上物品 =====
                double ox = Math.cos(curRad) * radius;
                double oz = Math.sin(curRad) * radius;

                pose.pushPose();
                pose.translate(ox, 0, oz);
                float bob = Mth.sin((time + ri * 7) * 0.12f) * 0.045f;
                pose.translate(0, bob, 0);
                pose.mulPose(Axis.YP.rotationDegrees(cur * 1.5f));
                pose.scale(0.42f, 0.42f, 0.42f);
                itemRenderer.renderStatic(item, ItemDisplayContext.GROUND, light, overlay,
                        pose, buffers, level, 0);
                pose.popPose();

                pose.popPose();
            }
        }

        /**
         * 绘制轨迹。
         * <p>
         * 用「面向相机的带状四边形」逐段生成，所以任何视角都有厚度。
         */
        private static void drawTrail(PoseStack pose, MultiBufferSource buffers,
                                      WardSourceBlockEntity.Ring ring,
                                      float curRad, Vec3 camPos,
                                      WardSourceBlockEntity be, float time) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

            final int SEG = 72;          // 总段数
            float radius = ring.radius;
            float width = ring.width;
            float baseAlpha = ring.alpha;

            int color = ring.color;
            float cr = ((color >> 16) & 255) / 255f;
            float cg = ((color >> 8) & 255) / 255f;
            float cb = (color & 255) / 255f;

            Matrix4f mat = pose.last().pose();

            // 世界坐标下的方块中心（用于计算相机朝向）
            double wx = be.getBlockPos().getX() + 0.5;
            double wy = be.getBlockPos().getY() + 0.5;
            double wz = be.getBlockPos().getZ() + 0.5;

            int style = ring.style;
            float spacing = Math.max(0.05f, Math.min(1.0f, ring.spacing));

            // 拖尾模式的长度（弧度）
            final float TRAIL_LEN = (float) (Math.PI * 1.2);

            for (int i = 0; i < SEG; i++) {
                float t0 = (float) i / SEG;
                float t1 = (float) (i + 1) / SEG;

                float a0 = t0 * (float) (Math.PI * 2);
                float a1 = t1 * (float) (Math.PI * 2);

                // ---- 该段是否绘制 + alpha 计算 ----
                float alpha0, alpha1;

                if (style == 0) {
                    // 连续环：首尾渐隐
                    alpha0 = fadeEnds(t0, 0.10f) * baseAlpha;
                    alpha1 = fadeEnds(t1, 0.10f) * baseAlpha;
                } else if (style == 1) {
                    // 拖尾：从物品位置往后延伸，越远越淡
                    float d0 = angDiff(a0, curRad);
                    float d1 = angDiff(a1, curRad);
                    if (d0 < 0) d0 += (float) (Math.PI * 2);
                    if (d1 < 0) d1 += (float) (Math.PI * 2);

                    if (d0 > TRAIL_LEN && d1 > TRAIL_LEN) continue;

                    alpha0 = Mth.clamp(1.0f - d0 / TRAIL_LEN, 0f, 1f) * baseAlpha;
                    alpha1 = Mth.clamp(1.0f - d1 / TRAIL_LEN, 0f, 1f) * baseAlpha;
                } else {
                    // 虚线：按 spacing 分段
                    if ((t0 % spacing) > spacing * 0.55f) continue;
                    alpha0 = baseAlpha;
                    alpha1 = baseAlpha;
                }

                if (alpha0 <= 0.001f && alpha1 <= 0.001f) continue;

                // ---- 该段的两端中心点 ----
                double c0x = Math.cos(a0) * radius;
                double c0z = Math.sin(a0) * radius;
                double c1x = Math.cos(a1) * radius;
                double c1z = Math.sin(a1) * radius;

                // ---- 计算相机方向，用于把段展开成面向相机的带 ----
                // 段中点（世界坐标）
                double mx = wx + (c0x + c1x) * 0.5;
                double my = wy;
                double mz = wz + (c0z + c1z) * 0.5;

                // 到相机的向量
                double vx = camPos.x - mx;
                double vy = camPos.y - my;
                double vz = camPos.z - mz;
                double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (vl < 1e-6) continue;
                vx /= vl; vy /= vl; vz /= vl;

                // 段的切线方向（世界，3D）
                double tx = c1x - c0x;
                double ty = 0.0;
                double tz = c1z - c0z;
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl < 1e-6) continue;
                tx /= tl; ty /= tl; tz /= tl;

                // 侧向量 = 切线 × 相机方向（叉积）
                double sx = ty * vz - tz * vy;
                double sy = tz * vx - tx * vz;
                double sz = tx * vy - ty * vx;
                double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1e-6) continue;
                sx = sx / sl * width;
                sy = sy / sl * width;
                sz = sz / sl * width;

                // 四边形：p0+s, p0-s, p1-s, p1+s
                float p0x = (float) c0x, p0z = (float) c0z;
                float p1x = (float) c1x, p1z = (float) c1z;

                vc.addVertex(mat, p0x + (float) sx, (float) sy, p0z + (float) sz).setColor(cr, cg, cb, alpha0);
                vc.addVertex(mat, p0x - (float) sx, (float) -sy, p0z - (float) sz).setColor(cr, cg, cb, alpha0);
                vc.addVertex(mat, p1x - (float) sx, (float) -sy, p1z - (float) sz).setColor(cr, cg, cb, alpha1);

                vc.addVertex(mat, p0x + (float) sx, (float) sy, p0z + (float) sz).setColor(cr, cg, cb, alpha0);
                vc.addVertex(mat, p1x - (float) sx, (float) -sy, p1z - (float) sz).setColor(cr, cg, cb, alpha1);
                vc.addVertex(mat, p1x + (float) sx, (float) sy, p1z + (float) sz).setColor(cr, cg, cb, alpha1);
            }
        }

        /** 首尾渐隐：t 在 0/1 附近时衰减 */
        private static float fadeEnds(float t, float fadeLen) {
            float a = Math.min(t / fadeLen, (1f - t) / fadeLen);
            return Mth.clamp(a, 0f, 1f);
        }

        /** 角度差（-PI ~ PI） */
        private static float angDiff(float a, float b) {
            float d = a - b;
            while (d > Math.PI) d -= (float) (Math.PI * 2);
            while (d < -Math.PI) d += (float) (Math.PI * 2);
            return d;
        }
    }
}