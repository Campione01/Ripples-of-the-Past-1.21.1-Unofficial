package rotp.addons.scarymonsters.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 1.16 vfx/scratch ClawSlash{Trigger,Instance,Geometry,Renderer}: short
 * additive claw arcs drawn in the world on the client. Call only from
 * client-side code.
 */
@EventBusSubscriber(modid = ScaryMonstersAddon.MOD_ID, value = Dist.CLIENT)
public final class ClawSlashRenderer {
    private static final List<Slash> ACTIVE = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private ClawSlashRenderer() {}

    // 1.16 ClawSlashTrigger.spawnSlash
    public static void spawnSlash(Vec3 pos, float yaw, float pitch, RandomSource random) {
        ACTIVE.add(Slash.random(pos, yaw, pitch, 0.9F, 1.0F, 140, 200, 255, 12));
        if (random.nextBoolean()) {
            Vec3 look = Vec3.directionFromRotation(pitch, yaw);
            ACTIVE.add(Slash.random(pos.add(look.scale(0.3F)),
                    yaw + (random.nextBoolean() ? 0.5F : -0.5F), pitch + 0.2F,
                    0.7F, 1.2F, 100, 170, 255, 10));
        }
    }

    // 1.16 ClawSlashTrigger.spawnSlashAtEntity: 2 blocks ahead of the eyes
    public static void spawnSlashAtEntity(LivingEntity entity) {
        Vec3 pos = entity.getEyePosition(1.0F).add(entity.getLookAngle().scale(2.0D));
        spawnSlash(pos, entity.getYRot(), entity.getXRot(), entity.getRandom());
    }

    // 1.16 ClawSlashTrigger.spawnSlashWhirlwind: 3 slashes scattered round the eyes
    public static void spawnSlashWhirlwind(LivingEntity entity) {
        RandomSource random = entity.getRandom();
        Vec3 base = entity.getEyePosition(1.0F);
        float entityYaw = entity.getYRot();
        for (int i = 0; i < 3; ++i) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.0D + random.nextDouble() * 2.2D;
            Vec3 pos = base.add(Math.cos(angle) * radius,
                    (random.nextDouble() - 0.5D) * 1.8D, Math.sin(angle) * radius);
            float yaw = entityYaw + (random.nextFloat() - 0.5F) * 200.0F;
            float pitch = (random.nextFloat() - 0.5F) * 70.0F;
            ACTIVE.add(Slash.random(pos, yaw, pitch, 0.8F, 1.1F, 140, 200, 255, 14));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Slash> iter = ACTIVE.iterator();
        while (iter.hasNext()) {
            if (--iter.next().lifetime <= 0) {
                iter.remove();
            }
        }
    }

    // 1.16 drew them in RenderWorldLastEvent, after the rest of the level
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || ACTIVE.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        PoseStack stack = event.getPoseStack();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (Slash slash : ACTIVE) {
            stack.pushPose();
            stack.translate(slash.pos.x - camera.x, slash.pos.y - camera.y, slash.pos.z - camera.z);
            int a = Math.min(255, Math.max(0, (int) (slash.alpha(partialTick) * slash.a)));
            stack.mulPose(Axis.YN.rotationDegrees(slash.yRot));
            stack.mulPose(Axis.XP.rotationDegrees(slash.xRot));
            stack.mulPose(Axis.ZP.rotationDegrees(slash.roll));
            drawClawSlash(buffer, stack.last().pose(), slash, a);
            stack.popPose();
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferUploader.drawWithShader(mesh);
        }
        finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    // 1.16 ClawSlashGeometry.drawClawSlash: 2-3 parallel claw arcs, thin at the ends
    private static void drawClawSlash(BufferBuilder buffer, Matrix4f matrix, Slash slash, int a) {
        Random rand = new Random(slash.seed);
        float scale = slash.scale;
        float arcLength = 2.0F + rand.nextFloat() * 0.6F;
        float startAngle = -arcLength * 0.5F;
        float endAngle = arcLength * 0.5F;
        float arcRadius = 2.0F * scale + rand.nextFloat() * 0.6F * scale;
        float baseWidth = (0.08F + rand.nextFloat() * 0.04F) * slash.thickness * scale;
        float heightSweep = 0.4F * scale + rand.nextFloat() * 0.2F * scale;
        float verticalOffset = -0.1F * scale + rand.nextFloat() * 0.2F * scale;
        int segments = 10;
        if (slash.mirror) {
            float temp = startAngle;
            startAngle = -endAngle;
            endAngle = -temp;
        }
        int clawCount = 2 + rand.nextInt(2);
        float clawSpread = (0.18F + rand.nextFloat() * 0.06F) * scale;
        float totalAngle = endAngle - startAngle;
        for (int claw = 0; claw < clawCount; ++claw) {
            float clawOffset = (claw - (clawCount - 1) * 0.5F) * clawSpread;
            for (int i = 0; i < segments; ++i) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                float angle0 = startAngle + t0 * totalAngle;
                float angle1 = startAngle + t1 * totalAngle;
                float widthFactor0 = (float) Math.sin(t0 * Math.PI);
                float widthFactor1 = (float) Math.sin(t1 * Math.PI);
                float w0 = baseWidth * widthFactor0;
                float w1 = baseWidth * widthFactor1;
                float z0 = (float) Math.cos(angle0) * arcRadius;
                float x0 = (float) Math.sin(angle0) * arcRadius;
                float z1 = (float) Math.cos(angle1) * arcRadius;
                float x1 = (float) Math.sin(angle1) * arcRadius;
                float y0 = (float) Math.sin(t0 * Math.PI) * heightSweep + verticalOffset;
                float y1 = (float) Math.sin(t1 * Math.PI) * heightSweep + verticalOffset;
                float tz0 = -((float) Math.sin(angle0));
                float tx0 = (float) Math.cos(angle0);
                float tz1 = -((float) Math.sin(angle1));
                float tx1 = (float) Math.cos(angle1);
                float cx0 = x0 + tx0 * clawOffset;
                float cz0 = z0 + tz0 * clawOffset;
                float cx1 = x1 + tx1 * clawOffset;
                float cz1 = z1 + tz1 * clawOffset;
                float nx0 = tz0;
                float nz0 = -tx0;
                float nx1 = tz1;
                float nz1 = -tx1;
                int alpha = (int) (a * ((widthFactor0 + widthFactor1) * 0.5F * 0.6F + 0.4F));
                vertex(buffer, matrix, cx0 - nx0 * w0, y0, cz0 - nz0 * w0, slash, alpha);
                vertex(buffer, matrix, cx1 - nx1 * w1, y1, cz1 - nz1 * w1, slash, alpha);
                vertex(buffer, matrix, cx0 + nx0 * w0, y0, cz0 + nz0 * w0, slash, alpha);
                vertex(buffer, matrix, cx1 - nx1 * w1, y1, cz1 - nz1 * w1, slash, alpha);
                vertex(buffer, matrix, cx1 + nx1 * w1, y1, cz1 + nz1 * w1, slash, alpha);
                vertex(buffer, matrix, cx0 + nx0 * w0, y0, cz0 + nz0 * w0, slash, alpha);
            }
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
            float x, float y, float z, Slash slash, int alpha) {
        buffer.addVertex(matrix, x, y, z).setColor(slash.r, slash.g, slash.b, alpha);
    }

    // 1.16 ClawSlashInstance
    private static final class Slash {
        final Vec3 pos;
        final float yRot;
        final float xRot;
        final float roll;
        final float scale;
        final float thickness;
        final int r;
        final int g;
        final int b;
        final int a;
        final int maxLifetime;
        final long seed;
        final boolean mirror;
        int lifetime;

        Slash(Vec3 pos, float yRot, float xRot, float roll, float scale, float thickness,
                int r, int g, int b, int a, int lifetime, long seed, boolean mirror) {
            this.pos = pos;
            this.yRot = yRot;
            this.xRot = xRot;
            this.roll = roll;
            this.scale = scale;
            this.thickness = thickness;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.lifetime = lifetime;
            this.maxLifetime = lifetime;
            this.seed = seed;
            this.mirror = mirror;
        }

        // fades in over the first 15% and out over the last 25%
        float alpha(float partialTick) {
            float progress = (maxLifetime - (lifetime - partialTick)) / maxLifetime;
            if (progress < 0.15F) {
                return progress / 0.15F;
            }
            if (progress > 0.75F) {
                return (1.0F - progress) / 0.25F;
            }
            return 1.0F;
        }

        // 1.16 ClawSlashInstance.createRandom
        static Slash random(Vec3 pos, float yaw, float pitch, float scale, float thickness,
                int r, int g, int b, int lifetime) {
            float yRot = yaw + (RANDOM.nextFloat() - 0.5F) * 0.6F;
            float xRot = pitch + (RANDOM.nextFloat() - 0.5F) * 0.4F - 0.3F;
            float[] rollOptions = {-35.0F, 0.0F, 35.0F};
            float roll = rollOptions[RANDOM.nextInt(rollOptions.length)]
                    + (RANDOM.nextFloat() - 0.5F) * 10.0F;
            float scaleVar = scale * (0.8F + RANDOM.nextFloat() * 0.4F);
            float thicknessVar = thickness * (0.7F + RANDOM.nextFloat() * 0.6F);
            int alpha = 200 + RANDOM.nextInt(56);
            boolean mirror = RANDOM.nextBoolean();
            return new Slash(pos, yRot, xRot, roll, scaleVar, thicknessVar,
                    r, g, b, alpha, lifetime, RANDOM.nextLong(), mirror);
        }
    }
}
