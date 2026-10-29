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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 中心旋转物品 + 星环渲染。
 * <p>
 * 中心是一个自转的悬浮物品；周围最多 5 个环，
 * 每环有独立的物品、半径、速度、起始角度、倾角。
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

            // ===== 中心物品 =====
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

            // ===== 逐环渲染 =====
            for (int ri = 0; ri < WardSourceBlockEntity.MAX_RINGS; ri++) {
                WardSourceBlockEntity.Ring ring = be.getRing(ri);
                ItemStack item = ring.item;
                if (item == null || item.isEmpty()) continue;

                float radius = ring.radius;
                float speed = ring.speed;
                float baseAngle = ring.angle;
                float tilt = ring.tilt;

                float cur = baseAngle + time * speed;

                pose.pushPose();
                pose.translate(0.5, cy, 0.5);

                if (tilt != 0f) {
                    pose.mulPose(Axis.XP.rotationDegrees(tilt));
                }

                // 环轨迹
                drawRing(pose, buffers, radius, ri);

                // 物品位置
                float rad = (float) Math.toRadians(cur);
                double ox = Math.cos(rad) * radius;
                double oz = Math.sin(rad) * radius;

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

        /** 画一个半透明发光圆环 */
        private static void drawRing(PoseStack pose, MultiBufferSource buffers, float radius, int index) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

            final int SEG = 56;
            final float width = 0.012f + (index == 0 ? 0.006f : 0f);

            var mat = pose.last().pose();

            float[][] COLORS = {
                    {0.55f, 0.88f, 1.00f},
                    {0.75f, 0.60f, 1.00f},
                    {1.00f, 0.85f, 0.45f},
                    {0.55f, 1.00f, 0.70f},
                    {1.00f, 0.60f, 0.60f},
            };
            float[] col = COLORS[index % COLORS.length];

            float a = 0.45f;

            for (int i = 0; i < SEG; i++) {
                double a0 = (Math.PI * 2) * i / SEG;
                double a1 = (Math.PI * 2) * (i + 1) / SEG;

                float x0 = (float) (Math.cos(a0) * (radius - width));
                float z0 = (float) (Math.sin(a0) * (radius - width));
                float x0o = (float) (Math.cos(a0) * (radius + width));
                float z0o = (float) (Math.sin(a0) * (radius + width));

                float x1 = (float) (Math.cos(a1) * (radius - width));
                float z1 = (float) (Math.sin(a1) * (radius - width));
                float x1o = (float) (Math.cos(a1) * (radius + width));
                float z1o = (float) (Math.sin(a1) * (radius + width));

                vc.addVertex(mat, x0, 0, z0).setColor(col[0], col[1], col[2], a);
                vc.addVertex(mat, x0o, 0, z0o).setColor(col[0], col[1], col[2], 0f);
                vc.addVertex(mat, x1o, 0, z1o).setColor(col[0], col[1], col[2], 0f);

                vc.addVertex(mat, x0, 0, z0).setColor(col[0], col[1], col[2], a);
                vc.addVertex(mat, x1o, 0, z1o).setColor(col[0], col[1], col[2], 0f);
                vc.addVertex(mat, x1, 0, z1).setColor(col[0], col[1], col[2], a);
            }
        }
    }
}
