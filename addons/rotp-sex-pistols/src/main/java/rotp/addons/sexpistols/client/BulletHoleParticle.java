package rotp.addons.sexpistols.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.sexpistols.particle.BulletHoleParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BulletHoleParticle
        extends TextureSheetParticle {
    private final Direction direction;
    private final BlockPos blockPos;
    private final BlockState initialState;
    private final int uOffset;
    private final int vOffset;
    private boolean renderReceiptRecorded;
    private boolean removalReceiptRecorded;
    private String removalReason = "EXPIRED";

    private BulletHoleParticle(
            BulletHoleParticleOptions options,
            ClientLevel level,
            double x,
            double y,
            double z) {
        super(level, x, y, z);
        direction = options.direction();
        blockPos = options.blockPos().immutable();
        initialState = level.getBlockState(blockPos);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getBlockRenderer()
                .getBlockModelShaper()
                .getTexture(initialState, level, blockPos);
        setSprite(sprite);
        uOffset = random.nextInt(16);
        vOffset = random.nextInt(16);
        hasPhysics = false;
        gravity = 0.0F;
        quadSize = 0.05F;
        lifetime = SexPistolsVisualDiagnostics
                .BULLET_HOLE_LIFETIME_MIN_TICKS
                + level.random.nextInt(120);
        setColor(0.22F, 0.22F, 0.22F);
        alpha = 0.95F;
        SexPistolsVisualDiagnostics.recordBulletHoleSpawn(
                direction, blockPos, lifetime);
        String invalidation = invalidationReason();
        if (invalidation != null) {
            removalReason = invalidation;
            remove();
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        String invalidation = invalidationReason();
        if (invalidation != null) {
            removalReason = invalidation;
            remove();
            return;
        }
        if (age++ >= lifetime) {
            removalReason = "EXPIRED";
            remove();
            return;
        }
        float fadeStart = lifetime * 0.82F;
        if (age > fadeStart) {
            float fade = 1.0F
                    - (age - fadeStart) / (lifetime - fadeStart);
            alpha = 0.95F * Mth.clamp(fade, 0.0F, 1.0F);
        }
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        if (!renderReceiptRecorded) {
            renderReceiptRecorded = true;
            SexPistolsVisualDiagnostics.recordBulletHoleRender();
        }
        Vec3 cameraPosition = camera.getPosition();
        float particleX = (float) (Mth.lerp(partialTick, xo, x)
                - cameraPosition.x);
        float particleY = (float) (Mth.lerp(partialTick, yo, y)
                - cameraPosition.y);
        float particleZ = (float) (Mth.lerp(partialTick, zo, z)
                - cameraPosition.z);
        Quaternionf rotation = direction.getRotation();
        Vector3f[] points = {
                new Vector3f(-1.0F, 0.01F, -1.0F),
                new Vector3f(-1.0F, 0.01F, 1.0F),
                new Vector3f(1.0F, 0.01F, 1.0F),
                new Vector3f(1.0F, 0.01F, -1.0F)
        };
        float scale = getQuadSize(partialTick);
        for (Vector3f point : points) {
            point.rotate(rotation)
                    .mul(scale)
                    .add(particleX, particleY, particleZ);
        }

        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = Math.max(12 - age / 20, 0);
        int lightColor = light << 20 | light << 4;
        float shade = 0.35F + light / 12.0F * 0.65F;
        float red = rCol * shade;
        float green = gCol * shade;
        float blue = bCol * shade;
        vertex(vertices, points[0], u1, v1, red, green, blue, lightColor);
        vertex(vertices, points[1], u1, v0, red, green, blue, lightColor);
        vertex(vertices, points[2], u0, v0, red, green, blue, lightColor);
        vertex(vertices, points[3], u0, v1, red, green, blue, lightColor);
    }

    @Override
    public void remove() {
        if (!removalReceiptRecorded) {
            removalReceiptRecorded = true;
            SexPistolsVisualDiagnostics.recordBulletHoleRemoved(
                    removalReason);
        }
        super.remove();
    }

    @Override
    protected float getU0() {
        return sprite.getU(uOffset / 16.0F);
    }

    @Override
    protected float getU1() {
        return sprite.getU((uOffset + 1) / 16.0F);
    }

    @Override
    protected float getV0() {
        return sprite.getV(vOffset / 16.0F);
    }

    @Override
    protected float getV1() {
        return sprite.getV((vOffset + 1) / 16.0F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    private String invalidationReason() {
        if (!level.hasChunkAt(blockPos)) {
            return "CHUNK_UNLOADED";
        }
        BlockState state = level.getBlockState(blockPos);
        if (!state.equals(initialState) || state.isAir()) {
            return "BLOCK_CHANGED";
        }
        VoxelShape shape = state.getCollisionShape(level, blockPos);
        if (shape.isEmpty()) {
            return "SURFACE_INVALID";
        }
        AABB blockBounds = shape.bounds().move(blockPos);
        return blockBounds.intersects(
                x - 0.1D,
                y - 0.1D,
                z - 0.1D,
                x + 0.1D,
                y + 0.1D,
                z + 0.1D)
                ? null : "SURFACE_INVALID";
    }

    public static void spawn(
            BulletHoleParticleOptions options,
            ClientLevel level,
            Vec3 position) {
        Minecraft.getInstance().particleEngine.add(
                new BulletHoleParticle(
                        options,
                        level,
                        position.x,
                        position.y,
                        position.z));
    }

    private void vertex(
            VertexConsumer vertices,
            Vector3f point,
            float u,
            float v,
            float red,
            float green,
            float blue,
            int light) {
        vertices.addVertex(point.x(), point.y(), point.z())
                .setUv(u, v)
                .setColor(red, green, blue, alpha)
                .setLight(light);
    }

    public static final class Factory
            implements ParticleProvider<BulletHoleParticleOptions> {
        public Factory() {}

        @Override
        public Particle createParticle(
                BulletHoleParticleOptions options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new BulletHoleParticle(options, level, x, y, z);
        }
    }
}
