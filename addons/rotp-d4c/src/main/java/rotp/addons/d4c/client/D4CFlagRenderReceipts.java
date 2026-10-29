package rotp.addons.d4c.client;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Read-only receipts emitted after the production flag model submits vertices. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = D4CAddon.MOD_ID, value = Dist.CLIENT)
public final class D4CFlagRenderReceipts {
    public static final int EXPECTED_FLAG_VERTEX_COUNT = 120;

    private static final int MAX_TRACKED_FLAGS = 64;
    private static final Map<UUID, Snapshot> LAST =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<UUID, Snapshot> eldest) {
                    return size() > MAX_TRACKED_FLAGS;
                }
            };
    private static long sequence;
    private static long renderFrameSequence;

    private D4CFlagRenderReceipts() {}

    @Nullable
    public static synchronized Snapshot snapshot(UUID entityUuid) {
        return LAST.get(entityUuid);
    }

    public static synchronized long currentRenderFrameSequence() {
        return renderFrameSequence;
    }

    @SubscribeEvent
    public static synchronized void onRenderFramePre(
            RenderFrameEvent.Pre event) {
        renderFrameSequence++;
    }

    static CountingVertexConsumer counting(VertexConsumer delegate) {
        return new CountingVertexConsumer(delegate);
    }

    static synchronized void record(
            D4CFlagRenderer renderer,
            D4CFlagModel model,
            D4CMarkerEntity entity,
            ResourceLocation texture,
            RenderType renderType,
            D4CFlagModel.RenderPose modelPose,
            float partialTick,
            float poseDeterminant,
            CountingVertexConsumer vertices) {
        Minecraft minecraft = Minecraft.getInstance();
        LAST.put(entity.getUUID(), new Snapshot(
                ++sequence,
                renderFrameSequence,
                entity.getUUID(),
                entity.getId(),
                entity.mode().name(),
                entity.ageTicks(),
                partialTick,
                entity.animationProgress(partialTick),
                minecraft.level != null
                        ? minecraft.level.getGameTime() : -1L,
                Util.getNanos(),
                renderer.getClass().getName(),
                model.getClass().getName(),
                D4CFlagModel.LAYER.toString(),
                texture.toString(),
                renderType.toString(),
                model.renderScaleX(),
                model.renderScaleY(),
                model.renderScaleZ(),
                poseDeterminant,
                modelPose.animationRootX(),
                modelPose.animationRootY(),
                modelPose.animationRootZ(),
                modelPose.animationRootXRot(),
                modelPose.animationRootYRot(),
                modelPose.animationRootZRot(),
                modelPose.centerXRot(),
                modelPose.lowerMiddleXRot(),
                modelPose.lowerTipXRot(),
                modelPose.upperMiddleXRot(),
                modelPose.upperTipXRot(),
                vertices.vertexCount(),
                entity.getX(),
                entity.getY(),
                entity.getZ()));
    }

    public record Snapshot(
            long sequence,
            long renderFrameSequence,
            UUID entityUuid,
            int entityId,
            String mode,
            int ageTicks,
            float partialTick,
            float animationProgress,
            long clientGameTime,
            long frameNanos,
            String rendererClass,
            String modelClass,
            String modelLayer,
            String textureId,
            String renderType,
            float renderScaleX,
            float renderScaleY,
            float renderScaleZ,
            float poseDeterminant,
            float animationRootX,
            float animationRootY,
            float animationRootZ,
            float animationRootXRot,
            float animationRootYRot,
            float animationRootZRot,
            float centerXRot,
            float lowerMiddleXRot,
            float lowerTipXRot,
            float upperMiddleXRot,
            float upperTipXRot,
            int vertexCount,
            double x,
            double y,
            double z) {}

    static final class CountingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private int vertexCount;

        private CountingVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        int vertexCount() {
            return vertexCount;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertexCount++;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                float red, float green, float blue, float alpha) {
            delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int alpha) {
            delegate.setWhiteAlpha(alpha);
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
        public VertexConsumer setLight(int packedLight) {
            delegate.setLight(packedLight);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int packedOverlay) {
            delegate.setOverlay(packedOverlay);
            return this;
        }

        @Override
        public VertexConsumer setNormal(
                float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int packedOverlay,
                int packedLight,
                float normalX,
                float normalY,
                float normalZ) {
            vertexCount++;
            delegate.addVertex(
                    x,
                    y,
                    z,
                    color,
                    u,
                    v,
                    packedOverlay,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay) {
            vertexCount += 4;
            delegate.putBulkData(
                    pose,
                    quad,
                    red,
                    green,
                    blue,
                    alpha,
                    packedLight,
                    packedOverlay);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float[] brightness,
                float red,
                float green,
                float blue,
                float alpha,
                int[] lightmap,
                int packedOverlay,
                boolean readAlpha) {
            vertexCount += 4;
            delegate.putBulkData(
                    pose,
                    quad,
                    brightness,
                    red,
                    green,
                    blue,
                    alpha,
                    lightmap,
                    packedOverlay,
                    readAlpha);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay,
                boolean readExistingColor) {
            vertexCount += 4;
            delegate.putBulkData(
                    pose,
                    quad,
                    red,
                    green,
                    blue,
                    alpha,
                    packedLight,
                    packedOverlay,
                    readExistingColor);
        }

        @Override
        public VertexConsumer misc(
                VertexFormatElement element, int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }

        @Override
        public int applyBakedLighting(
                int packedLight, ByteBuffer data) {
            return delegate.applyBakedLighting(packedLight, data);
        }

        @Override
        public void applyBakedNormals(
                Vector3f generated,
                ByteBuffer data,
                Matrix3f normalTransform) {
            delegate.applyBakedNormals(
                    generated, data, normalTransform);
        }
    }
}
