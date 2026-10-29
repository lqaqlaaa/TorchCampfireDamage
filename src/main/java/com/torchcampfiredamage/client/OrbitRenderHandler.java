package com.torchcampfiredamage.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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

/**
 * 守卫火把的「光环 + 旋转物品」渲染。
 * <p>
 * 在火把周围画一个水平的发光圆环，环上均匀分布 1~4 个物品，绕中心旋转。
 */
public class OrbitRenderHandler {

    @EventBusSubscriber(modid = "torchcampfiredamage", value = Dist.CLIENT)
    public static class Registrar {
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(
                    com.torchcampfiredamage.block.ModBlocks.WARD_SOURCE_BE.get(),
                    OrbitRenderer::new);
        }
    }

    /** 实际渲染器 */
    public static class OrbitRenderer implements BlockEntityRenderer<WardSourceBlockEntity> {
        private final ItemRenderer itemRenderer;

        public OrbitRenderer(BlockEntityRendererProvider.Context ctx) {
            this.itemRenderer = ctx.getItemRenderer();
        }

        @Override
        public void render(WardSourceBlockEntity be, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light, int overlay) {
            if (!be.isOrbitVisible()) return;

            ItemStack[] items = new ItemStack[4];
            int count = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack st = be.getOrbitItem(i);
                items[i] = st;
                if (!st.isEmpty()) count++;
            }
            if (count == 0) return;

            float radius = be.getOrbitRadius();

            // 用系统时间做旋转基准，保证客户端平滑
            float time = (Minecraft.getInstance().level == null ? 0
                    : Minecraft.getInstance().level.getGameTime()) + partialTick;
            float angle = (time * 2.0f) % 360.0f;   // 每 20tick 转 40°，约 1.8 秒一圈

            Vec3 center = new Vec3(0.5, 0.55, 0.5);

            // ===== 1. 发光圆环 =====
            pose.pushPose();
            pose.translate(center.x, center.y, center.z);
            drawRing(pose, buffers, radius, light);
            pose.popPose();

            // ===== 2. 环上的物品 =====
            int slot = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack st = items[i];
                if (st.isEmpty()) continue;

                float a = (float) Math.toRadians(angle + slot * (360.0f / count));

                double ox = Math.cos(a) * radius;
                double oz = Math.sin(a) * radius;

                pose.pushPose();
                pose.translate(center.x + ox, center.y + 0.08, center.z + oz);

                // 物品轻微悬浮波动
                float bob = Mth.sin((time + i * 10) * 0.15f) * 0.05f;
                pose.translate(0, bob, 0);

                // 自转
                pose.mulPose(Axis.YP.rotationDegrees(angle * 2.0f + i * 90.0f));
                pose.scale(0.55f, 0.55f, 0.55f);

                itemRenderer.renderStatic(st, ItemDisplayContext.GROUND, light, overlay,
                        pose, buffers, be.getLevel(), 0);

                pose.popPose();
                slot++;
            }
        }

        /** 画一个水平发光圆环（由若干小段组成） */
        private static void drawRing(PoseStack pose, MultiBufferSource buffers, float radius, int light) {
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());

            final int SEG = 48;
            final float width = 0.035f;

            var mat = pose.last().pose();
            float a = 0.55f;

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

                // 两个三角形组成一段环
                vc.addVertex(mat, x0, 0, z0).setColor(0.75f, 0.9f, 1.0f, a);
                vc.addVertex(mat, x0o, 0, z0o).setColor(0.75f, 0.9f, 1.0f, 0f);
                vc.addVertex(mat, x1o, 0, z1o).setColor(0.75f, 0.9f, 1.0f, 0f);

                vc.addVertex(mat, x0, 0, z0).setColor(0.75f, 0.9f, 1.0f, a);
                vc.addVertex(mat, x1o, 0, z1o).setColor(0.75f, 0.9f, 1.0f, 0f);
                vc.addVertex(mat, x1, 0, z1).setColor(0.75f, 0.9f, 1.0f, a);
            }
        }
    }
}