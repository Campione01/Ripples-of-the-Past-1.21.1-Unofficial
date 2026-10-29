package rotp.addons.d4c.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public abstract class D4CParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private D4CParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        pickSprite(sprites);
    }

    protected final void refreshSprite() {
        pickSprite(sprites);
    }

    protected final boolean beginManualTick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return false;
        }
        return true;
    }

    protected final void renderRibbon(
            VertexConsumer vertices,
            Camera camera,
            float partialTick,
            float halfWidth,
            float length) {
        Vec3 center = relativePosition(camera, partialTick);
        Vec3 direction = center.normalize();
        Vec3 widthAxis = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (widthAxis.lengthSqr() < 1.0E-8D) {
            Vector3f left = camera.getLeftVector();
            widthAxis = new Vec3(left.x(), left.y(), left.z());
        } else {
            widthAxis = widthAxis.normalize();
        }
        Vec3 lengthAxis = widthAxis.cross(direction);
        if (lengthAxis.lengthSqr() < 1.0E-8D) {
            Vector3f up = camera.getUpVector();
            lengthAxis = new Vec3(up.x(), up.y(), up.z());
        } else {
            lengthAxis = lengthAxis.normalize();
        }
        widthAxis = widthAxis.scale(halfWidth);
        lengthAxis = lengthAxis.scale(length);

        Vec3 first = center.subtract(widthAxis);
        Vec3 second = center.add(widthAxis);
        Vec3 third = second.add(lengthAxis);
        Vec3 fourth = first.add(lengthAxis);
        int light = getLightColor(partialTick);
        vertex(vertices, first, getU1(), getV1(), light);
        vertex(vertices, second, getU0(), getV1(), light);
        vertex(vertices, third, getU0(), getV0(), light);
        vertex(vertices, fourth, getU1(), getV0(), light);
    }

    protected final void renderCameraSquare(
            VertexConsumer vertices,
            Camera camera,
            float partialTick,
            float halfSize,
            float rotationDegrees) {
        Vector3f lookVector = camera.getLookVector();
        Vector3f upVector = camera.getUpVector();
        Vec3 look = new Vec3(
                lookVector.x(), lookVector.y(), lookVector.z());
        Vec3 up = new Vec3(
                upVector.x(), upVector.y(), upVector.z());
        Vec3 firstAxis = up.cross(look).normalize();
        Vec3 secondAxis = look.cross(firstAxis).normalize();
        double angle = Math.toRadians(rotationDegrees);
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        Vec3 rotatedFirst = firstAxis.scale(cosine)
                .subtract(secondAxis.scale(sine))
                .scale(halfSize);
        Vec3 rotatedSecond = firstAxis.scale(sine)
                .add(secondAxis.scale(cosine))
                .scale(halfSize);
        Vec3 center = relativePosition(camera, partialTick);
        int light = getLightColor(partialTick);
        vertex(
                vertices,
                center.subtract(rotatedFirst).subtract(rotatedSecond),
                getU0(),
                getV1(),
                light);
        vertex(
                vertices,
                center.subtract(rotatedFirst).add(rotatedSecond),
                getU0(),
                getV0(),
                light);
        vertex(
                vertices,
                center.add(rotatedFirst).add(rotatedSecond),
                getU1(),
                getV0(),
                light);
        vertex(
                vertices,
                center.add(rotatedFirst).subtract(rotatedSecond),
                getU1(),
                getV1(),
                light);
    }

    protected final Vec3 relativePosition(
            Camera camera, float partialTick) {
        Vec3 cameraPosition = camera.getPosition();
        return new Vec3(
                Mth.lerp((double) partialTick, xo, x)
                        - cameraPosition.x,
                Mth.lerp((double) partialTick, yo, y)
                        - cameraPosition.y,
                Mth.lerp((double) partialTick, zo, z)
                        - cameraPosition.z);
    }

    protected final void vertex(
            VertexConsumer vertices,
            Vec3 position,
            float u,
            float v,
            int light) {
        vertices.addVertex(
                        (float) position.x,
                        (float) position.y,
                        (float) position.z)
                .setUv(u, v)
                .setColor(rCol, gCol, bCol, alpha)
                .setLight(light);
    }

    private static Player nearestPlayer(
            ClientLevel level,
            double x,
            double y,
            double z,
            double maxDistanceSquared) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player player : level.players()) {
            double distance = player.distanceToSqr(x, y, z);
            if (distance < nearestDistance
                    && distance < maxDistanceSquared) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static final class GoldenLightPillarParticle
            extends D4CParticle {
        private static final float HALF_WIDTH = 0.02F;
        private static final float LENGTH = 8.0F;
        private final int ownerId;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final float riseSpeed;
        private float rise;

        private GoldenLightPillarParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            lifetime = 30;
            gravity = 0.0F;
            hasPhysics = false;
            quadSize = LENGTH;
            riseSpeed = 0.05F
                    + (float) Math.random() * 0.2F;
            Player owner = nearestPlayer(
                    level, x, y, z, 25.0D);
            if (owner != null) {
                ownerId = owner.getId();
                offsetX = x - owner.getX();
                offsetY = y - owner.getY();
                offsetZ = z - owner.getZ();
            } else {
                ownerId = -1;
                offsetX = 0.0D;
                offsetY = 0.0D;
                offsetZ = 0.0D;
            }
            setColor(1.0F, 1.0F, 1.0F);
            alpha = 0.8F;
        }

        @Override
        public void tick() {
            if (!beginManualTick()) {
                return;
            }
            rise += riseSpeed;
            Entity owner = ownerId != -1
                    ? level.getEntity(ownerId)
                    : null;
            if (owner instanceof Player player) {
                setPos(
                        player.getX() + offsetX,
                        player.getY() + offsetY + rise,
                        player.getZ() + offsetZ);
            } else {
                setPos(x, y + riseSpeed, z);
            }
            float progress = (float) age / lifetime;
            if (progress < 0.1F) {
                alpha = progress / 0.1F * 0.9F;
            } else if (progress > 0.6F) {
                float fade = (progress - 0.6F) / 0.4F;
                alpha = (1.0F - fade * fade) * 0.9F;
            } else {
                alpha = 0.9F;
            }
            refreshSprite();
        }

        @Override
        public void render(
                VertexConsumer vertices,
                Camera camera,
                float partialTick) {
            renderRibbon(
                    vertices,
                    camera,
                    partialTick,
                    HALF_WIDTH,
                    LENGTH);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_LIT;
        }
    }

    private static final class ShortGoldenLightPillarParticle
            extends D4CParticle {
        private static final float HALF_WIDTH = 0.015F;
        private static final float LENGTH = 1.0F;
        private final int ownerId;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final float riseSpeed;
        private float rise;

        private ShortGoldenLightPillarParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            lifetime = 15;
            gravity = 0.0F;
            hasPhysics = false;
            quadSize = LENGTH;
            riseSpeed = 0.08F
                    + (float) Math.random() * 0.25F;
            Player owner = nearestPlayer(
                    level, x, y, z, 25.0D);
            if (owner != null) {
                ownerId = owner.getId();
                offsetX = x - owner.getX();
                offsetY = y - owner.getY();
                offsetZ = z - owner.getZ();
            } else {
                ownerId = -1;
                offsetX = 0.0D;
                offsetY = 0.0D;
                offsetZ = 0.0D;
            }
            setColor(1.0F, 0.8F, 0.3F);
            alpha = 0.7F;
        }

        @Override
        public void tick() {
            if (!beginManualTick()) {
                return;
            }
            rise += riseSpeed;
            Entity owner = ownerId != -1
                    ? level.getEntity(ownerId)
                    : null;
            if (owner instanceof Player player) {
                setPos(
                        player.getX() + offsetX,
                        player.getY() + offsetY + rise,
                        player.getZ() + offsetZ);
            } else {
                setPos(x, y + riseSpeed, z);
            }
            float progress = (float) age / lifetime;
            if (progress < 0.15F) {
                alpha = progress / 0.15F * 0.8F;
            } else if (progress > 0.5F) {
                float fade = (progress - 0.5F) / 0.5F;
                alpha = (1.0F - fade * fade) * 0.8F;
            } else {
                alpha = 0.8F;
            }
            refreshSprite();
        }

        @Override
        public void render(
                VertexConsumer vertices,
                Camera camera,
                float partialTick) {
            renderRibbon(
                    vertices,
                    camera,
                    partialTick,
                    HALF_WIDTH,
                    LENGTH);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_LIT;
        }
    }

    private static final class WideGoldenLightPillarParticle
            extends D4CParticle {
        private static final float LENGTH = 8.0F;
        private final int ownerId;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final float halfWidth;
        private final float fallSpeed;
        private float fall;

        private WideGoldenLightPillarParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double requestedWidth,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            lifetime = 30;
            gravity = 0.0F;
            hasPhysics = false;
            quadSize = LENGTH;
            halfWidth = (float) (
                    requestedWidth != 0.0D
                            ? requestedWidth
                            : 0.3D);
            fallSpeed = 0.05F
                    + (float) Math.random() * 0.2F;
            Player owner = nearestPlayer(
                    level, x, y, z, 25.0D);
            if (owner != null) {
                ownerId = owner.getId();
                offsetX = x - owner.getX();
                offsetY = y - owner.getY();
                offsetZ = z - owner.getZ();
            } else {
                ownerId = -1;
                offsetX = 0.0D;
                offsetY = 0.0D;
                offsetZ = 0.0D;
            }
            setColor(1.0F, 1.0F, 1.0F);
            alpha = 0.8F;
        }

        @Override
        public void tick() {
            if (!beginManualTick()) {
                return;
            }
            fall += fallSpeed;
            Entity owner = ownerId != -1
                    ? level.getEntity(ownerId)
                    : null;
            if (owner instanceof Player player) {
                setPos(
                        player.getX() + offsetX,
                        player.getY() + offsetY - fall,
                        player.getZ() + offsetZ);
            }
            refreshSprite();
            if ((double) age > (double) lifetime * 0.8D) {
                float fade = (age - lifetime * 0.8F)
                        / (lifetime * 0.2F);
                alpha = 0.8F * (1.0F - fade);
            }
        }

        @Override
        public void render(
                VertexConsumer vertices,
                Camera camera,
                float partialTick) {
            renderRibbon(
                    vertices,
                    camera,
                    partialTick,
                    halfWidth,
                    LENGTH);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_LIT;
        }
    }

    private static final class GoldLightParticle
            extends D4CParticle {
        private final double sourceXSpeed;
        private final double sourceYSpeed;
        private final double sourceZSpeed;

        private GoldLightParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            sourceXSpeed = xSpeed;
            sourceYSpeed = ySpeed;
            sourceZSpeed = zSpeed;
            lifetime = 20 + (int) (Math.random() * 40.0D);
            gravity = 0.0F;
            hasPhysics = false;
            quadSize = 0.1F
                    + (float) Math.random() * 0.1F;
            setColor(
                    0.9F + (float) Math.random() * 0.1F,
                    0.8F + (float) Math.random() * 0.2F,
                    0.3F + (float) Math.random() * 0.4F);
            alpha = 0.9F;
        }

        @Override
        public void tick() {
            if (!beginManualTick()) {
                return;
            }
            xd = sourceXSpeed;
            yd = sourceYSpeed;
            zd = sourceZSpeed;
            setPos(x + xd, y + yd, z + zd);
            float progress = (float) age / lifetime;
            if (progress < 0.2F) {
                alpha = progress / 0.2F * 0.9F;
            } else if (progress > 0.7F) {
                float fade = (progress - 0.7F) / 0.3F;
                alpha = (1.0F - fade) * 0.9F;
            } else {
                alpha = 0.9F;
            }
            if (Math.random() < 0.1D) {
                alpha *= 0.7F;
            }
            refreshSprite();
        }

        @Override
        public void render(
                VertexConsumer vertices,
                Camera camera,
                float partialTick) {
            Vec3 center = relativePosition(camera, partialTick);
            float angle = (age + partialTick) * 0.1F;
            float cosine = Mth.cos(angle);
            float sine = Mth.sin(angle);
            float size = getQuadSize(partialTick);
            Vec3[] points = {
                    new Vec3(
                            -size * cosine - size * sine,
                            -size * cosine + size * sine,
                            0.0D).add(center),
                    new Vec3(
                            -size * cosine + size * sine,
                            -size * cosine - size * sine,
                            0.0D).add(center),
                    new Vec3(
                            size * cosine + size * sine,
                            size * cosine - size * sine,
                            0.0D).add(center),
                    new Vec3(
                            size * cosine - size * sine,
                            size * cosine + size * sine,
                            0.0D).add(center)
            };
            int light = getLightColor(partialTick);
            vertex(vertices, points[0], getU1(), getV1(), light);
            vertex(vertices, points[1], getU1(), getV0(), light);
            vertex(vertices, points[2], getU0(), getV0(), light);
            vertex(vertices, points[3], getU0(), getV1(), light);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_LIT;
        }
    }

    private static final class LightBallParticle
            extends D4CParticle {
        private final Vec3 start;
        private final Vec3 target;
        private final float initialSize;

        private LightBallParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double targetX,
                double targetY,
                double targetZ,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            lifetime = 45;
            gravity = 0.0F;
            hasPhysics = false;
            initialSize = 0.05F
                    + (float) Math.random() * 0.025F;
            quadSize = initialSize;
            start = new Vec3(x, y, z);
            target = new Vec3(targetX, targetY, targetZ);
            setColor(
                    0.95F + (float) Math.random() * 0.05F,
                    0.85F + (float) Math.random() * 0.15F,
                    0.4F + (float) Math.random() * 0.5F);
            alpha = 1.0F;
        }

        @Override
        public void tick() {
            if (!beginManualTick()) {
                return;
            }
            float progress = (float) age / lifetime;
            float eased = progress * progress
                    * progress * progress;
            setPos(
                    Mth.lerp((double) eased, start.x, target.x),
                    Mth.lerp((double) eased, start.y, target.y),
                    Mth.lerp((double) eased, start.z, target.z));
            quadSize = initialSize
                    * (1.0F - progress * 0.3F);
            alpha = 1.0F - progress * 0.2F;
            refreshSprite();
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    private static final class AnnihilateParticle
            extends D4CParticle {
        private AnnihilateParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            xd = xSpeed * 0.4D;
            yd = ySpeed * 0.4D;
            zd = zSpeed * 0.4D;
            setColor(1.0F, 1.0F, 1.0F);
            quadSize *= 0.75F;
            lifetime = (int) (
                    60.0D
                            / (Math.random() * 0.8D + 0.6D));
            lifetime = (int) (lifetime * 0.75F);
            hasPhysics = true;
        }

        @Override
        public void tick() {
            if (!beginManualTick()) {
                return;
            }
            move(xd, yd, zd);
            xd *= 0.96F;
            yd *= 0.96F;
            zd *= 0.96F;
            if (onGround) {
                xd *= 0.7F;
                zd *= 0.7F;
            }
            quadSize *= 0.92F;
            if (quadSize < 0.02F) {
                remove();
                return;
            }
            rCol *= 0.96F;
            gCol *= 0.96F;
            bCol *= 0.96F;
        }

        @Override
        protected int getLightColor(float partialTick) {
            int block = 180;
            int sky = 180;
            return block | sky << 16;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    private static final class DevilSkyParticle
            extends D4CParticle {
        private static final float BASE_SIZE = 40.0F;
        private static final float ROTATION_PER_TICK = 15.0F;
        private static final float PULSE_SPEED = 0.15F;
        private final int ownerId;
        private float rotation;
        private float pulse = 0.05F;
        private boolean stopping;
        private SkullHeadParticle skull;

        private DevilSkyParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            lifetime = Integer.MAX_VALUE;
            gravity = 0.0F;
            hasPhysics = false;
            quadSize = BASE_SIZE;
            Player owner = nearestPlayer(
                    level, x, y, z, 2500.0D);
            ownerId = owner != null ? owner.getId() : -1;
            setColor(1.0F, 1.0F, 1.0F);
            alpha = 0.8F;
            Particle child = Minecraft.getInstance()
                    .particleEngine
                    .createParticle(
                            D4CAddon.SKULLHEAD.get(),
                            x,
                            y,
                            z,
                            0.0D,
                            0.0D,
                            0.0D);
            if (child instanceof SkullHeadParticle skullParticle) {
                skull = skullParticle;
            }
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (stopping) {
                if (skull != null) {
                    skull.stop();
                    skull = null;
                }
                remove();
                return;
            }
            rotation += ROTATION_PER_TICK;
            if (rotation >= 360.0F) {
                rotation -= 360.0F;
            }
            pulse = 1.0F
                    + Mth.sin(age * PULSE_SPEED) * 0.15F;
            if (ownerId != -1
                    && !(level.getEntity(ownerId)
                            instanceof Player)) {
                stopping = true;
            }
            if (skull != null && skull.isAlive()) {
                skull.setPos(x, y, z);
            }
            age++;
        }

        private void stop() {
            stopping = true;
            if (skull != null) {
                skull.stop();
                skull = null;
            }
        }

        @Override
        public void render(
                VertexConsumer vertices,
                Camera camera,
                float partialTick) {
            renderCameraSquare(
                    vertices,
                    camera,
                    partialTick,
                    BASE_SIZE * pulse,
                    rotation
                            + ROTATION_PER_TICK * partialTick);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    private static final class SkullHeadParticle
            extends D4CParticle {
        private static final float HALF_SIZE = 6.0F;
        private final int ownerId;
        private boolean stopping;

        private SkullHeadParticle(
                ClientLevel level,
                double x,
                double y,
                double z,
                SpriteSet sprites) {
            super(level, x, y, z, sprites);
            lifetime = Integer.MAX_VALUE;
            gravity = 0.0F;
            hasPhysics = false;
            quadSize = HALF_SIZE;
            Player owner = nearestPlayer(
                    level, x, y, z, 2500.0D);
            ownerId = owner != null ? owner.getId() : -1;
            setColor(1.0F, 1.0F, 1.0F);
            alpha = 0.9F;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (stopping) {
                remove();
                return;
            }
            if (ownerId != -1
                    && !(level.getEntity(ownerId)
                            instanceof Player)) {
                stopping = true;
            }
            age++;
        }

        private void stop() {
            stopping = true;
        }

        @Override
        public void render(
                VertexConsumer vertices,
                Camera camera,
                float partialTick) {
            renderCameraSquare(
                    vertices,
                    camera,
                    partialTick,
                    HALF_SIZE,
                    0.0F);
        }

        @Override
        protected int getLightColor(float partialTick) {
            return LightTexture.FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    public static final class GoldenLightPillarProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public GoldenLightPillarProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new GoldenLightPillarParticle(
                    level, x, y, z, sprites);
        }
    }

    public static final class ShortGoldenLightPillarProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ShortGoldenLightPillarProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new ShortGoldenLightPillarParticle(
                    level, x, y, z, sprites);
        }
    }

    public static final class AnnihilateProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public AnnihilateProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new AnnihilateParticle(
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    sprites);
        }
    }

    public static final class LightBallProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public LightBallProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new LightBallParticle(
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    sprites);
        }
    }

    public static final class WideGoldenLightPillarProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public WideGoldenLightPillarProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new WideGoldenLightPillarParticle(
                    level, x, y, z, xSpeed, sprites);
        }
    }

    public static final class GoldLightProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public GoldLightProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new GoldLightParticle(
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    sprites);
        }
    }

    public static final class DevilSkyProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public DevilSkyProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new DevilSkyParticle(
                    level, x, y, z, sprites);
        }
    }

    public static final class SkullHeadProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SkullHeadProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new SkullHeadParticle(
                    level, x, y, z, sprites);
        }
    }
}
