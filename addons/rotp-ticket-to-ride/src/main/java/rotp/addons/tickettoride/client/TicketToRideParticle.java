package rotp.addons.tickettoride.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The five Love Train particles of 1.16 client/Particle (registered in ClientInit): every client draws them, at full
 * brightness, translucent. The receipts feed the visual acceptance diagnostics.
 */
public abstract class TicketToRideParticle
        extends TextureSheetParticle {
    private static final int FULL_BRIGHT = 0xF000F0;

    protected final SpriteSet sprites;
    private final String particleId;
    private boolean diagnosticRemovalRecorded;

    protected TicketToRideParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            SpriteSet sprites,
            String particleId) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.particleId = particleId;
        gravity = 0.0F;
        hasPhysics = false;
        TicketToRideClientDiagnostics.recordParticleCreated(
                particleId);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        renderQuad(vertices, camera, partialTick);
        TicketToRideClientDiagnostics.recordParticleRendered(
                particleId, camera.getEntity(), 4);
    }

    protected abstract void renderQuad(
            VertexConsumer vertices, Camera camera, float partialTick);

    @Override
    public void remove() {
        if (!diagnosticRemovalRecorded) {
            diagnosticRemovalRecorded = true;
            TicketToRideClientDiagnostics.recordParticleRemoved();
        }
        super.remove();
    }

    protected Vec3 cameraRelative(Camera camera, float partialTick) {
        Vec3 cameraPos = camera.getPosition();
        return new Vec3(
                Mth.lerp(partialTick, xo, x) - cameraPos.x,
                Mth.lerp(partialTick, yo, y) - cameraPos.y,
                Mth.lerp(partialTick, zo, z) - cameraPos.z);
    }

    protected void vertex(
            VertexConsumer vertices, Vec3 at, float u, float v) {
        vertices.addVertex((float) at.x, (float) at.y, (float) at.z)
                .setUv(u, v)
                .setColor(rCol, gCol, bCol, alpha)
                .setLight(FULL_BRIGHT);
    }

    /** A strip facing the camera, from the point up by height, width each side. */
    protected void cameraFacingStrip(
            VertexConsumer vertices, Vec3 pos, double width, double height) {
        Vec3 look = pos.normalize();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = right.cross(look).normalize();
        Vec3 bottomLeft = pos.subtract(right.scale(width));
        Vec3 bottomRight = pos.add(right.scale(width));
        Vec3 topRight = bottomRight.add(up.scale(height));
        Vec3 topLeft = bottomLeft.add(up.scale(height));
        vertex(vertices, bottomLeft, getU1(), getV1());
        vertex(vertices, bottomRight, getU0(), getV1());
        vertex(vertices, topRight, getU0(), getV0());
        vertex(vertices, topLeft, getU1(), getV0());
    }

    // 1.16 findNearestPlayer: the nearest player within 5 blocks
    protected static Player nearestPlayer(
            ClientLevel level, double x, double y, double z) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Player player) {
                double distance = player.distanceToSqr(x, y, z);
                if (distance < nearestDistance && distance < 25.0D) {
                    nearestDistance = distance;
                    nearest = player;
                }
            }
        }
        return nearest;
    }

    // 1.16 GoldLightParticle (love_train_light): gold-tinted, 20 to 59
    // ticks, keeps its starting motion, fades in and out with flicker, a
    // flat quad in the world's XY plane that spins.
    static final class GoldLight extends TicketToRideParticle {
        private final double initialX;
        private final double initialY;
        private final double initialZ;

        GoldLight(ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, SpriteSet sprites, String id) {
            super(level, x, y, z, sprites, id);
            initialX = xd;
            initialY = yd;
            initialZ = zd;
            lifetime = 20 + random.nextInt(40);
            quadSize = 0.1F + random.nextFloat() * 0.1F;
            setColor(0.9F + random.nextFloat() * 0.1F,
                    0.8F + random.nextFloat() * 0.2F,
                    0.3F + random.nextFloat() * 0.4F);
            alpha = 0.9F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            xd = initialX;
            yd = initialY;
            zd = initialZ;
            x += xd;
            y += yd;
            z += zd;
            float ageRatio = (float) age / lifetime;
            if (ageRatio < 0.2F) {
                alpha = ageRatio / 0.2F * 0.9F;
            }
            else if (ageRatio > 0.7F) {
                alpha = (1.0F - (ageRatio - 0.7F) / 0.3F) * 0.9F;
            }
            else {
                alpha = 0.9F;
            }
            if (random.nextFloat() < 0.1F) {
                alpha *= 0.7F;
            }
            setSpriteFromAge(sprites);
        }

        @Override
        protected void renderQuad(
                VertexConsumer vertices, Camera camera, float partialTick) {
            Vec3 at = cameraRelative(camera, partialTick);
            float rotation = (age + partialTick) * 0.1F;
            float size = getQuadSize(partialTick);
            float cos = Mth.cos(rotation);
            float sin = Mth.sin(rotation);
            vertex(vertices, at.add(-size * cos - size * sin, -size * cos + size * sin, 0.0D), getU1(), getV1());
            vertex(vertices, at.add(-size * cos + size * sin, -size * cos - size * sin, 0.0D), getU1(), getV0());
            vertex(vertices, at.add(size * cos + size * sin, size * cos - size * sin, 0.0D), getU0(), getV0());
            vertex(vertices, at.add(size * cos - size * sin, size * cos + size * sin, 0.0D), getU0(), getV1());
        }
    }

    // 1.16 GoldenLightPillarParticle (love_train_light_pillar, 8 high, 30
    // ticks, white) and ShortGoldenLightPillarParticle (love_train_light_short,
    // 1 high, 15 ticks, gold): a thin camera-facing strip rising from where it
    // was spawned, carried along with the nearest player within 5 blocks.
    static final class Pillar extends TicketToRideParticle {
        private final boolean tall;
        private final float height;
        private final float width;
        private final float upwardSpeed;
        private final double relativeX;
        private final double relativeZ;
        private final double baseYOffset;
        private final int targetPlayerId;
        private float upwardOffset;

        Pillar(ClientLevel level, double x, double y, double z,
                SpriteSet sprites, String id, boolean tall) {
            super(level, x, y, z, sprites, id);
            this.tall = tall;
            lifetime = tall ? 30 : 15;
            height = tall ? 8.0F : 1.0F;
            width = tall ? 0.02F : 0.015F;
            upwardSpeed = tall
                    ? (float) (0.05D + random.nextDouble() * 0.2D)
                    : (float) (0.08D + random.nextDouble() * 0.25D);
            Player player = nearestPlayer(level, x, y, z);
            if (player != null) {
                targetPlayerId = player.getId();
                relativeX = x - player.getX();
                relativeZ = z - player.getZ();
                baseYOffset = y - player.getY();
            }
            else {
                targetPlayerId = -1;
                relativeX = 0.0D;
                relativeZ = 0.0D;
                baseYOffset = 0.0D;
            }
            if (tall) {
                setColor(1.0F, 1.0F, 1.0F);
                alpha = 0.8F;
            }
            else {
                setColor(1.0F, 0.8F, 0.3F);
                alpha = 0.7F;
            }
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            upwardOffset += upwardSpeed;
            Entity target = targetPlayerId != -1
                    ? level.getEntity(targetPlayerId)
                    : null;
            if (target instanceof Player player) {
                x = player.getX() + relativeX;
                z = player.getZ() + relativeZ;
                y = player.getY() + baseYOffset + upwardOffset;
            }
            else {
                y += upwardSpeed;
            }
            float ageRatio = (float) age / lifetime;
            float fadeIn = tall ? 0.1F : 0.15F;
            float fadeOut = tall ? 0.6F : 0.5F;
            float full = tall ? 0.9F : 0.8F;
            if (ageRatio < fadeIn) {
                alpha = ageRatio / fadeIn * full;
            }
            else if (ageRatio > fadeOut) {
                float out = (ageRatio - fadeOut) / (1.0F - fadeOut);
                alpha = (1.0F - out * out) * full;
            }
            else {
                alpha = full;
            }
            setSpriteFromAge(sprites);
        }

        @Override
        protected void renderQuad(
                VertexConsumer vertices, Camera camera, float partialTick) {
            cameraFacingStrip(vertices, cameraRelative(camera, partialTick),
                    width, height);
        }

        @Override
        public AABB getRenderBoundingBox(float partialTicks) {
            return AABB.INFINITE;
        }
    }

    // 1.16 LightBallParticle (love_train_light_ball): its "speed" is the
    // point it eases towards over 45 ticks, shrinking and fading a little.
    static final class Ball extends TicketToRideParticle {
        private final float initialScale;
        private final Vec3 start;
        private final Vec3 target;

        Ball(ClientLevel level, double x, double y, double z,
                double tx, double ty, double tz, SpriteSet sprites, String id) {
            super(level, x, y, z, sprites, id);
            lifetime = 45;
            initialScale = 0.05F + random.nextFloat() * 0.025F;
            quadSize = initialScale;
            start = new Vec3(x, y, z);
            target = new Vec3(tx, ty, tz);
            setColor(0.95F + random.nextFloat() * 0.05F,
                    0.85F + random.nextFloat() * 0.15F,
                    0.4F + random.nextFloat() * 0.5F);
            alpha = 1.0F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            float progress = (float) age / lifetime;
            float eased = progress * progress * progress * progress;
            x = Mth.lerp(eased, start.x, target.x);
            y = Mth.lerp(eased, start.y, target.y);
            z = Mth.lerp(eased, start.z, target.z);
            quadSize = initialScale * (1.0F - progress * 0.3F);
            alpha = 1.0F - progress * 0.2F;
            setSpriteFromAge(sprites);
        }

        @Override
        protected void renderQuad(
                VertexConsumer vertices, Camera camera, float partialTick) {
            float size = getQuadSize(partialTick);
            cameraFacingStrip(vertices, cameraRelative(camera, partialTick),
                    size, size);
        }
    }

    // 1.16 LightWallParticle (love_train_light_wall): an 8-high strip as wide
    // as its x "speed" (else 0.3), sinking with the nearest player, fading
    // over its last fifth.
    static final class Wall extends TicketToRideParticle {
        private final float width;
        private final float downwardSpeed;
        private final double relativeX;
        private final double relativeZ;
        private final double baseYOffset;
        private final int targetPlayerId;
        private float downwardOffset;

        Wall(ClientLevel level, double x, double y, double z,
                double xd, SpriteSet sprites, String id) {
            super(level, x, y, z, sprites, id);
            lifetime = 30;
            width = xd != 0.0D ? (float) xd : 0.3F;
            downwardSpeed = (float) (0.05D + random.nextDouble() * 0.2D);
            setColor(1.0F, 1.0F, 1.0F);
            alpha = 0.8F;
            Player player = nearestPlayer(level, x, y, z);
            if (player != null) {
                targetPlayerId = player.getId();
                relativeX = x - player.getX();
                relativeZ = z - player.getZ();
                baseYOffset = y - player.getY();
            }
            else {
                targetPlayerId = -1;
                relativeX = 0.0D;
                relativeZ = 0.0D;
                baseYOffset = 0.0D;
            }
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            downwardOffset += downwardSpeed;
            if (targetPlayerId != -1
                    && level.getEntity(targetPlayerId) instanceof Player player) {
                x = player.getX() + relativeX;
                z = player.getZ() + relativeZ;
                y = player.getY() + baseYOffset - downwardOffset;
            }
            if (age > lifetime * 0.8F) {
                float fade = (age - lifetime * 0.8F) / (lifetime * 0.2F);
                alpha = 0.8F * (1.0F - fade);
            }
            setSpriteFromAge(sprites);
        }

        @Override
        protected void renderQuad(
                VertexConsumer vertices, Camera camera, float partialTick) {
            cameraFacingStrip(vertices, cameraRelative(camera, partialTick),
                    width, 8.0D);
        }

        @Override
        public AABB getRenderBoundingBox(float partialTicks) {
            return AABB.INFINITE;
        }
    }

    public static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double dx,
                double dy,
                double dz) {
            String id = BuiltInRegistries.PARTICLE_TYPE
                    .getKey(type).toString();
            return switch (BuiltInRegistries.PARTICLE_TYPE
                    .getKey(type).getPath()) {
                case "love_train_light_pillar" ->
                        new Pillar(level, x, y, z, sprites, id, true);
                case "love_train_light_short" ->
                        new Pillar(level, x, y, z, sprites, id, false);
                case "love_train_light_ball" ->
                        new Ball(level, x, y, z, dx, dy, dz, sprites, id);
                case "love_train_light_wall" ->
                        new Wall(level, x, y, z, dx, sprites, id);
                default ->
                        new GoldLight(level, x, y, z, dx, dy, dz, sprites, id);
            };
        }
    }
}
