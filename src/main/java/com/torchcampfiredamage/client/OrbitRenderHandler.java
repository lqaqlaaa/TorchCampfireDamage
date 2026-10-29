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
 * 轨迹有三种样式（对应 {@link WardSourceBlockEntity.Ring#style}）：
 * <pre>
 *  0 连续环 —— 完整的一圈细线，像行星环，首尾各一小段渐隐
 *  1 拖尾   —— 只画物品后方一段，尾巴越远越淡，像彗星
 *  2 虚线   —— 一圈均匀断开的小段，每段两端渐隐
 * </pre>
 * 所有轨迹都用「面向相机的薄带」绘制，任何视角都有厚度。
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
        /** 轨迹细分段数（越大越平滑） */
        private static final int SEG = 128;

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
            Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            /* ===== 中心物品 ===== */
            ItemStack center = be.getCenterItem();
            if (center != null && !center.isEmpty()) {
                pose.pushPose();
                pose.translate(0.5, cy, 0.5);
                pose.translate(0, Mth.sin(time * 0.08f) * 0.03f, 0);
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
                float cur = ring.angle + time * ring.speed;
                float curRad = (float) Math.toRadians(cur);

                pose.pushPose();
                pose.translate(0.5, cy, 0.5);
                if (ring.tilt != 0f) {
                    pose.mulPose(Axis.XP.rotationDegrees(ring.tilt));
                }

                // ---- 轨迹 ----
                drawRingTrail(pose, buffers, ring, curRad, camPos, be);

                // ---- 物品 ----
                pose.pushPose();
                pose.translate(Math.cos(curRad) * radius, 0, Math.sin(curRad) * radius);
                pose.translate(0, Mth.sin((time + ri * 7) * 0.12f) * 0.045f, 0);
                pose.mulPose(Axis.YP.rotationDegrees(cur * 1.5f));
                pose.scale(0.42f, 0.42f, 0.42f);
                itemRenderer.renderStatic(item, ItemDisplayContext.GROUND, light, overlay,
                        pose, buffers, level, 0);
                pose.popPose();

                pose.popPose();
            }
        }

        /* ================= 轨迹 ================= */

        private static void drawRingTrail(PoseStack pose, MultiBufferSource buffers,
                                          WardSourceBlockEntity.Ring ring,
                                          float curRad, Vec3 camPos,
                                          WardSourceBlockEntity be) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

            final float radius = ring.radius;
            final float halfWidth = Math.max(0.004f, ring.width) * 0.5f;
            final float baseAlpha = ring.alpha;

            final int color = ring.color;
            final float cr = ((color >> 16) & 255) / 255f;
            final float cg = ((color >> 8) & 255) / 255f;
            final float cb = (color & 255) / 255f;

            final int style = ring.style;
            // 虚线密度：spacing 越小，虚线段越密
            final float spacing = Mth.clamp(ring.spacing, 0.05f, 1.0f);

            Matrix4f mat = pose.last().pose();

            // 方块中心的世界坐标（用于算相机朝向）
            double wx = be.getBlockPos().getX() + 0.5;
            double wy = be.getBlockPos().getY() + 0.5;
            double wz = be.getBlockPos().getZ() + 0.5;

            // 拖尾覆盖的角度（弧度）：占整圈的 1/3
            final float TRAIL_SPAN = (float) (Math.PI * 2 / 3.0);

            for (int i = 0; i < SEG; i++) {
                float t0 = i / (float) SEG;
                float t1 = (i + 1) / (float) SEG;

                float a0 = t0 * (float) (Math.PI * 2);
                float a1 = t1 * (float) (Math.PI * 2);

                float alpha0, alpha1;

                if (style == 0) {
                    // ===== 连续环：完整一圈，首尾轻微渐隐 =====
                    alpha0 = baseAlpha;
                    alpha1 = baseAlpha;

                } else if (style == 1) {
                    // ===== 拖尾：只画物品后方一段 =====
                    // 计算该段与物品角度的"落后量"（0 = 在物品处）
                    float d0 = behind(a0, curRad);
                    float d1 = behind(a1, curRad);
                    if (d0 > TRAIL_SPAN && d1 > TRAIL_SPAN) continue;

                    // 越靠近头部越亮
                    float k0 = 1f - Math.min(d0 / TRAIL_SPAN, 1f);
                    float k1 = 1f - Math.min(d1 / TRAIL_SPAN, 1f);
                    // 平方让尾巴衰减更自然
                    alpha0 = baseAlpha * k0 * k0;
                    alpha1 = baseAlpha * k1 * k1;

                } else {
                    // ===== 虚线：均匀断开 =====
                    // 每段虚线占 spacing 的比例
                    float onLen = spacing * 0.6f;
                    float phase0 = t0 * 4f;   // 一圈分成 4 组
                    float phase1 = t1 * 4f;
                    boolean on0 = (phase0 - (int) phase0) < onLen;
                    boolean on1 = (phase1 - (int) phase1) < onLen;
                    if (!on0 && !on1) continue;

                    // 段内渐隐（两端淡，中间实）
                    float f0 = on0 ? edgeFade(phase0 - (int) phase0, onLen) : 0f;
                    float f1 = on1 ? edgeFade(phase1 - (int) phase1, onLen) : 0f;
                    alpha0 = baseAlpha * f0;
                    alpha1 = baseAlpha * f1;
                }

                if (alpha0 <= 0.004f && alpha1 <= 0.004f) continue;

                // ---- 该段两端点 ----
                double x0 = Math.cos(a0) * radius;
                double z0 = Math.sin(a0) * radius;
                double x1 = Math.cos(a1) * radius;
                double z1 = Math.sin(a1) * radius;

                // ---- 面向相机的侧向量 ----
                double mxW = wx + (x0 + x1) * 0.5;
                double mzW = wz + (z0 + z1) * 0.5;

                double vx = camPos.x - mxW;
                double vy = camPos.y - wy;
                double vz = camPos.z - mzW;
                double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (vl < 1e-5) continue;
                vx /= vl; vy /= vl; vz /= vl;

                double tx = x1 - x0, ty = 0, tz = z1 - z0;
                double tl = Math.sqrt(tx * tx + tz * tz);
                if (tl < 1e-7) continue;
                tx /= tl; ty /= tl; tz /= tl;

                // S = T × V
                double sx = ty * vz - tz * vy;
                double sy = tz * vx - tx * vz;
                double sz = tx * vy - ty * vx;
                double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1e-7) continue;
                sx = sx / sl * halfWidth;
                sy = sy / sl * halfWidth;
                sz = sz / sl * halfWidth;

                float fx0 = (float) x0, fz0 = (float) z0;
                float fx1 = (float) x1, fz1 = (float) z1;
                float fsx = (float) sx, fsy = (float) sy, fsz = (float) sz;

                // 四边形（两三角）
                vc.addVertex(mat, fx0 + fsx, fsy, fz0 + fsz).setColor(cr, cg, cb, alpha0);
                vc.addVertex(mat, fx0 - fsx, -fsy, fz0 - fsz).setColor(cr, cg, cb, alpha0);
                vc.addVertex(mat, fx1 - fsx, -fsy, fz1 - fsz).setColor(cr, cg, cb, alpha1);

                vc.addVertex(mat, fx0 + fsx, fsy, fz0 + fsz).setColor(cr, cg, cb, alpha0);
                vc.addVertex(mat, fx1 - fsx, -fsy, fz1 - fsz).setColor(cr, cg, cb, alpha1);
                vc.addVertex(mat, fx1 + fsx, fsy, fz1 + fsz).setColor(cr, cg, cb, alpha1);
            }
        }

        /** 该角度点比 curRad "落后"多少（0 ~ 2π） */
        private static float behind(float a, float curRad) {
            float d = curRad - a;
            d = (float) (d % (Math.PI * 2));
            if (d < 0) d += (float) (Math.PI * 2);
            return d;
        }

        /** 段内边缘渐隐：两端 15% 淡入淡出 */
        private static float edgeFade(float p, float len) {
            float edge = Math.max(1e-4f, len * 0.15f);
            float f = Math.min(p / edge, (len - p) / edge);
            return Mth.clamp(f, 0f, 1f);
        }
    }
}