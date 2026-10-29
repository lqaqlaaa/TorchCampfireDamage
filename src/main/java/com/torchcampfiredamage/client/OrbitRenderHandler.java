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
 * 轨迹用「连续折线带」绘制：相邻两段共享同一顶点，所以不会出现断点。
 * <ul>
 *   <li>0 连续环 —— 完整一圈，像行星环</li>
 *   <li>1 拖尾   —— 物品后方 120°，头亮尾淡</li>
 *   <li>2 虚线   —— 一圈分 8 组短划，段内两端淡</li>
 * </ul>
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

    /**
     * 自定义渲染层：位置+颜色，半透明混合，不写深度，不剔除背面。
     * <p>
     * 关键：比 RenderType.lightning() 的「闪电分段」更可控，
     * 画出的四边形是完整连续的，不会被打断成点。
     */
    public static final net.minecraft.client.renderer.RenderType TRAIL_RENDER_TYPE =
            net.minecraft.client.renderer.RenderType.create(
                    "torchcampfiredamage_trail",
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    256,
                    false,
                    false,
                    net.minecraft.client.renderer.RenderType.CompositeState.builder()
                            .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(
                                    net.minecraft.client.renderer.GameRenderer::getPositionColorShader))
                            .setTransparencyState(new net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard(
                                    "trail_translucent",
                                    () -> {
                                        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                                        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                                    },
                                    () -> {
                                        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                                    }))
                            .setCullState(new net.minecraft.client.renderer.RenderStateShard.CullStateShard(false))
                            .setDepthTestState(new net.minecraft.client.renderer.RenderStateShard.DepthTestStateShard(
                                    "trail_depth", 515))
                            .setWriteMaskState(new net.minecraft.client.renderer.RenderStateShard.WriteMaskStateShard(
                                    true, false))
                            .createCompositeState(false));

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

                drawTrail(pose, buffers, ring, curRad, camPos, be);

                // 物品
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

        /* ================= 轨迹（连续折线带） ================= */

        private static void drawTrail(PoseStack pose, MultiBufferSource buffers,
                                      WardSourceBlockEntity.Ring ring,
                                      float curRad, Vec3 camPos,
                                      WardSourceBlockEntity be) {
            VertexConsumer vc = buffers.getBuffer(TRAIL_RENDER_TYPE);

            final float radius = ring.radius;
            final float halfWidth = Math.max(0.006f, ring.width) * 0.5f;
            final float baseAlpha = ring.alpha;

            final int color = ring.color;
            final float cr = ((color >> 16) & 255) / 255f;
            final float cg = ((color >> 8) & 255) / 255f;
            final float cb = (color & 255) / 255f;

            final int style = ring.style;
            final float spacing = Mth.clamp(ring.spacing, 0.05f, 1.0f);

            // 分段数：按半径自适应，保证段长不会小于线宽（避免断点）
            int seg = (int) Math.max(48, Math.min(256, radius * 96));

            Matrix4f mat = pose.last().pose();

            double wx = be.getBlockPos().getX() + 0.5;
            double wy = be.getBlockPos().getY() + 0.5;
            double wz = be.getBlockPos().getZ() + 0.5;

            // 拖尾覆盖角度
            final float TRAIL_SPAN = (float) Math.toRadians(120);

            // 虚线：一圈分成 8 组
            final int DASH_GROUPS = 8;

            // ---- 先计算每个采样点的：位置 + alpha + 是否绘制 ----
            float[] px_ = new float[seg + 1];
            float[] pz_ = new float[seg + 1];
            float[] pa_ = new float[seg + 1];
            boolean[] pd_ = new boolean[seg + 1];

            for (int i = 0; i <= seg; i++) {
                float t = i / (float) seg;
                float a = t * (float) (Math.PI * 2);

                px_[i] = (float) (Math.cos(a) * radius);
                pz_[i] = (float) (Math.sin(a) * radius);

                float alpha;
                boolean draw = true;

                if (style == 0) {
                    alpha = baseAlpha;
                } else if (style == 1) {
                    float d = behind(a, curRad);
                    if (d > TRAIL_SPAN) {
                        draw = false;
                        alpha = 0;
                    } else {
                        float k = 1f - d / TRAIL_SPAN;
                        alpha = baseAlpha * k * k;
                    }
                } else {
                    // 虚线：8 组，每组占 spacing
                    float ph = t * DASH_GROUPS;
                    float frac = ph - (float) Math.floor(ph);
                    if (frac >= spacing) {
                        draw = false;
                        alpha = 0;
                    } else {
                        // 段内两端渐隐
                        float e = Math.max(1e-3f, spacing * 0.18f);
                        float f = Math.min(frac / e, (spacing - frac) / e);
                        alpha = baseAlpha * Mth.clamp(f, 0f, 1f);
                    }
                }

                pd_[i] = draw;
                pa_[i] = Mth.clamp(alpha, 0f, 1f);
            }

            // ---- 逐段生成带（相邻段共享顶点，保证连续） ----
            for (int i = 0; i < seg; i++) {
                if (!pd_[i] && !pd_[i + 1]) continue;
                if (pa_[i] <= 0.002f && pa_[i + 1] <= 0.002f) continue;

                float x0 = px_[i], z0 = pz_[i];
                float x1 = px_[i + 1], z1 = pz_[i + 1];

                // 该段中点 → 相机方向
                double mwX = wx + (x0 + x1) * 0.5;
                double mwZ = wz + (z0 + z1) * 0.5;
                double vx = camPos.x - mwX;
                double vy = camPos.y - wy;
                double vz = camPos.z - mwZ;
                double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (vl < 1e-5) continue;
                vx /= vl; vy /= vl; vz /= vl;

                // 切线
                double tx = x1 - x0, ty = 0, tz = z1 - z0;
                double tl = Math.sqrt(tx * tx + tz * tz);
                if (tl < 1e-7) continue;
                tx /= tl; ty /= tl; tz /= tl;

                // 侧向量 = T × V
                double sx = ty * vz - tz * vy;
                double sy = tz * vx - tx * vz;
                double sz = tx * vy - ty * vx;
                double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl < 1e-7) continue;
                sx = sx / sl * halfWidth;
                sy = sy / sl * halfWidth;
                sz = sz / sl * halfWidth;

                float fsx = (float) sx, fsy = (float) sy, fsz = (float) sz;

                // QUADS 模式：一个四边形 4 个顶点（按顺序绕一圈）
                //  v0 = 起点上方   v1 = 起点下方
                //  v3 = 终点上方   v2 = 终点下方
                vc.addVertex(mat, x0 + fsx, fsy, z0 + fsz).setColor(cr, cg, cb, pa_[i]);
                vc.addVertex(mat, x0 - fsx, -fsy, z0 - fsz).setColor(cr, cg, cb, pa_[i]);
                vc.addVertex(mat, x1 - fsx, -fsy, z1 - fsz).setColor(cr, cg, cb, pa_[i + 1]);
                vc.addVertex(mat, x1 + fsx, fsy, z1 + fsz).setColor(cr, cg, cb, pa_[i + 1]);
            }
        }

        /** 该角度点比 curRad 落后多少（0 ~ 2π） */
        private static float behind(float a, float curRad) {
            float d = curRad - a;
            d = (float) (d % (Math.PI * 2));
            if (d < 0) d += (float) (Math.PI * 2);
            return d;
        }
    }
}