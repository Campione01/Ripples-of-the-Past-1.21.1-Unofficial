package rotp.addons.kraftwork.client;

import java.nio.ByteBuffer;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import rotp.addons.kraftwork.entity.KraftWorkItemProjectile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Opt-in acceptance receipts emitted only after the production item renderer
 * has completed a visible Kraft Work projectile draw.
 */
public final class KraftWorkItemRenderDiagnostics {
    private static boolean enabled;
    @Nullable
    private static UUID expectedOwnerUuid;
    private static long sequence;
    private static long baselineSequence;
    private static Snapshot latest = Snapshot.awaiting(0L, null);

    private KraftWorkItemRenderDiagnostics() {}

    public static synchronized void begin(UUID ownerUuid) {
        enabled = true;
        expectedOwnerUuid = ownerUuid;
        baselineSequence = sequence;
        latest = Snapshot.awaiting(sequence, ownerUuid);
    }

    public static synchronized void end() {
        enabled = false;
        expectedOwnerUuid = null;
    }

    public static synchronized Snapshot snapshot() {
        return latest;
    }

    static synchronized boolean observing() {
        return enabled;
    }

    static CountingMultiBufferSource counting(
            MultiBufferSource delegate) {
        return new CountingMultiBufferSource(delegate);
    }

    static void recordCompletedDraw(
            KraftWorkItemProjectile projectile,
            StandEntity controller,
            int packedLight,
            int submittedVertices) {
        if (!enabled) {
            return;
        }
        Entity projectileOwner = projectile.getOwner();
        LivingEntity controllerUser = controller.getUser();
        UUID ownerUuid = projectileOwner != null
                ? projectileOwner.getUUID()
                : controllerUser != null
                        ? controllerUser.getUUID()
                        : null;
        synchronized (KraftWorkItemRenderDiagnostics.class) {
            if (!enabled
                    || expectedOwnerUuid == null
                    || !expectedOwnerUuid.equals(ownerUuid)) {
                return;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                    projectile.getItem().getItem());
            long drawSequence = ++sequence;
            latest = new Snapshot(
                    drawSequence,
                    drawSequence - baselineSequence,
                    System.nanoTime(),
                    RenderSystem.isOnRenderThread(),
                    true,
                    projectile.getUUID(),
                    controller.getUUID(),
                    ownerUuid,
                    itemId != null
                            ? itemId.toString()
                            : "unregistered",
                    projectile.getItem().getCount(),
                    projectile.isAlive() && !projectile.isRemoved(),
                    submittedVertices,
                    // drawn opaque, as 1.16's SpriteRenderer drew it
                    1.0F,
                    packedLight,
                    KraftWorkItemProjectileRenderer.class.getName(),
                    "GROUND");
        }
    }

    public record Snapshot(
            long sequence,
            long drawCountSinceBegin,
            long renderNanos,
            boolean renderThread,
            boolean renderCompleted,
            @Nullable UUID projectileUuid,
            @Nullable UUID controllerUuid,
            @Nullable UUID ownerUuid,
            String itemId,
            int itemCount,
            boolean projectileAlive,
            int submittedVertices,
            float effectiveAlpha,
            int packedLight,
            String rendererClass,
            String displayContext) {
        private static Snapshot awaiting(
                long sequence,
                @Nullable UUID ownerUuid) {
            return new Snapshot(
                    sequence,
                    0L,
                    -1L,
                    false,
                    false,
                    null,
                    null,
                    ownerUuid,
                    "not_observed",
                    0,
                    false,
                    0,
                    -1.0F,
                    0,
                    KraftWorkItemProjectileRenderer.class.getName(),
                    "GROUND");
        }
    }

    static final class CountingMultiBufferSource
            implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private int submittedVertices;

        private CountingMultiBufferSource(
                MultiBufferSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new CountingVertexConsumer(
                    delegate.getBuffer(renderType), this);
        }

        int submittedVertices() {
            return submittedVertices;
        }

        private void addVertices(int count) {
            submittedVertices += Math.max(count, 0);
        }
    }

    private static final class CountingVertexConsumer
            implements VertexConsumer {
        private final VertexConsumer delegate;
        private final CountingMultiBufferSource counter;

        private CountingVertexConsumer(
                VertexConsumer delegate,
                CountingMultiBufferSource counter) {
            this.delegate = delegate;
            this.counter = counter;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            counter.addVertices(1);
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
            counter.addVertices(1);
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
            counter.addVertices(4);
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
            counter.addVertices(4);
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
            counter.addVertices(4);
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
                VertexFormatElement element,
                int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }

        @Override
        public int applyBakedLighting(
                int packedLight, ByteBuffer data) {
            return delegate.applyBakedLighting(
                    packedLight, data);
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
