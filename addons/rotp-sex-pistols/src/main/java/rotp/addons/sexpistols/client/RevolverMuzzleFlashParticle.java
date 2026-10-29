package rotp.addons.sexpistols.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class RevolverMuzzleFlashParticle
        extends TextureSheetParticle {
    public enum FlashKind {
        REVOLVER,
        KICK_NORMAL,
        KICK_CRITICAL
    }

    private final FlashKind flashKind;
    private boolean renderReceiptRecorded;
    private boolean removalReceiptRecorded;

    private RevolverMuzzleFlashParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites,
            FlashKind flashKind) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.flashKind = flashKind;
        pickSprite(sprites);
        hasPhysics = false;
        gravity = 0.0F;
        xd = xSpeed;
        yd = ySpeed;
        zd = zSpeed;
        lifetime = SexPistolsVisualDiagnostics.MUZZLE_LIFETIME_TICKS;
        quadSize = 0.21F + random.nextFloat() * 0.09F;
        setColor(
                1.0F,
                0.88F + random.nextFloat() * 0.12F,
                0.58F + random.nextFloat() * 0.2F);
        alpha = 1.0F;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        if (flashKind == FlashKind.REVOLVER) {
            SexPistolsVisualDiagnostics.recordMuzzleSpawn();
        }
        else {
            SexPistolsVisualDiagnostics.recordKickSpawn(
                    flashKind == FlashKind.KICK_CRITICAL);
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        x += xd;
        y += yd;
        z += zd;
        xd *= 0.62D;
        yd *= 0.62D;
        zd *= 0.62D;
        float progress = (float) age / lifetime;
        alpha = Mth.clamp(1.0F - progress * progress, 0.0F, 1.0F);
        quadSize *= 0.86F;
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        if (!renderReceiptRecorded) {
            renderReceiptRecorded = true;
            if (flashKind == FlashKind.REVOLVER) {
                SexPistolsVisualDiagnostics.recordMuzzleRender();
            }
            else {
                SexPistolsVisualDiagnostics.recordKickRender();
            }
        }
        super.render(vertices, camera, partialTick);
    }

    @Override
    public void remove() {
        if (!removalReceiptRecorded) {
            removalReceiptRecorded = true;
            if (flashKind == FlashKind.REVOLVER) {
                SexPistolsVisualDiagnostics.recordMuzzleRemoved();
            }
            else {
                SexPistolsVisualDiagnostics.recordKickRemoved();
            }
        }
        super.remove();
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = ((float) age + partialTick) / lifetime;
        float flashScale = 1.0F
                + (1.0F - Mth.clamp(progress, 0.0F, 1.0F))
                        * 0.35F;
        return quadSize * flashScale;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    public static final class Factory
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final FlashKind flashKind;

        public Factory(SpriteSet sprites) {
            this(sprites, FlashKind.REVOLVER);
        }

        public Factory(SpriteSet sprites, FlashKind flashKind) {
            this.sprites = sprites;
            this.flashKind = flashKind;
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
            return new RevolverMuzzleFlashParticle(
                    level,
                    x,
                    y,
                    z,
                    xSpeed,
                    ySpeed,
                    zSpeed,
                    sprites,
                    flashKind);
        }
    }
}
